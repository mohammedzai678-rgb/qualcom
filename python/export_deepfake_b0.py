"""
Export deepfake_b0.onnx with verified FaceForensics++ weights.

Steps:
  1. Load efficientnet_b0_ffpp_c23.pth into EfficientNet-B0 (2-class head)
  2. Run sanity inference — verify outputs are NOT near-uniform (random weights fail this)
  3. Export to ONNX (opset 18, self-contained single file)
  4. Write SHA-256 checksum to deepfake_b0.onnx.sha256

Usage:
    python export_deepfake_b0.py
"""

from __future__ import annotations

import sys
import io

# Force UTF-8 output on Windows to avoid cp1252 encoding errors
# (torch.onnx verbose messages may contain Unicode characters)
if hasattr(sys.stdout, 'buffer'):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

import hashlib
from pathlib import Path

import torch
import torch.nn.functional as F
from torchvision.models import efficientnet_b0

# ─── Paths ────────────────────────────────────────────────────────────────────
SCRIPT_DIR   = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent
WEIGHTS_PATH = SCRIPT_DIR / "efficientnet_b0_ffpp_c23.pth"
MODELS_DIR   = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "models"
ONNX_OUT     = MODELS_DIR / "deepfake_b0.onnx"
SHA256_OUT   = MODELS_DIR / "deepfake_b0.onnx.sha256"

# ─── Sanity thresholds ────────────────────────────────────────────────────────
# A trained model on identical synthetic images should NOT output a probability
# near 0.50/0.50. Random weights will hover tightly around 0.50 for any input.
# We test two maximally different inputs and require the gap between their
# real-class probabilities to exceed this threshold.
SANITY_MIN_RESPONSE_GAP = 0.05   # trained model must differ by at least 5 pp
SANITY_MIN_CONFIDENCE   = 0.52   # at least one input must not be dead-center


def build_model() -> torch.nn.Module:
    """Construct EfficientNet-B0 with a 2-class deepfake detection head."""
    model = efficientnet_b0(weights=None)
    in_features = model.classifier[1].in_features
    model.classifier = torch.nn.Sequential(
        torch.nn.Dropout(0.4),
        torch.nn.Linear(in_features, 2),
    )
    return model


def load_weights(model: torch.nn.Module, path: Path) -> bool:
    """
    Load .pth checkpoint into the model with key-prefix remapping.
    Returns True if weights were successfully loaded with meaningful coverage.
    """
    print(f"  Loading weights from {path} ({path.stat().st_size:,} bytes) ...")
    state_dict = torch.load(path, map_location="cpu", weights_only=True)

    # Unwrap common checkpoint wrappers
    if "model" in state_dict:
        state_dict = state_dict["model"]
    elif "state_dict" in state_dict:
        state_dict = state_dict["state_dict"]

    # Remap key prefixes (e.g. "efficientnet." or "module." or "net.")
    clean = {}
    for k, v in state_dict.items():
        new_k = k
        for prefix in ("efficientnet.", "module.", "net.", "model."):
            if new_k.startswith(prefix):
                new_k = new_k[len(prefix):]
                break
        clean[new_k] = v

    result = model.load_state_dict(clean, strict=False)
    missing  = [k for k in result.missing_keys  if "num_batches_tracked" not in k]
    unexpected = result.unexpected_keys

    coverage = 1.0 - len(missing) / max(len(clean), 1)
    print(f"  Weight coverage: {coverage:.1%}  "
          f"| missing={len(missing)}  unexpected={len(unexpected)}")

    if coverage < 0.5:
        print(f"  ERROR: Less than 50% of keys matched — weights likely incompatible.")
        if missing[:5]:
            print(f"  First missing keys: {missing[:5]}")
        return False

    if missing:
        print(f"  Warning: {len(missing)} keys missing (acceptable if classifier head differs).")
    return True


def sanity_check(model: torch.nn.Module) -> bool:
    """
    Run two maximally different inputs and verify the model responds non-uniformly.

    A random/untrained model outputs logits ~ [0, 0] -> softmax ~ [0.50, 0.50]
    for ANY input. A trained model should show at least a small directional
    difference between a blank (likely-REAL) and a patterned (ambiguous) input.
    """
    print("  Running sanity inference ...")
    model.eval()

    with torch.no_grad():
        # Input A: all-gray image (neutral, featureless — trained model leans REAL)
        gray  = torch.full((1, 3, 224, 224), 0.5)
        # Input B: random noise (high frequency — some models flag as anomalous)
        noise = torch.rand(1, 3, 224, 224)
        # Input C: black image
        black = torch.zeros(1, 3, 224, 224)

        logits_gray  = model(gray)
        logits_noise = model(noise)
        logits_black = model(black)

        prob_gray  = F.softmax(logits_gray,  dim=1)[0]
        prob_noise = F.softmax(logits_noise, dim=1)[0]
        prob_black = F.softmax(logits_black, dim=1)[0]

        real_gray  = prob_gray[0].item()   # index 0 = REAL
        real_noise = prob_noise[0].item()
        real_black = prob_black[0].item()

    print(f"  P(real): gray={real_gray:.4f}  noise={real_noise:.4f}  black={real_black:.4f}")

    gap = max(
        abs(real_gray  - real_noise),
        abs(real_gray  - real_black),
        abs(real_noise - real_black),
    )
    max_conf = max(real_gray, real_noise, real_black,
                   1-real_gray, 1-real_noise, 1-real_black)

    print(f"  Response gap={gap:.4f}  max_confidence={max_conf:.4f}")

    if gap < SANITY_MIN_RESPONSE_GAP:
        print(f"  FAIL: Gap {gap:.4f} < threshold {SANITY_MIN_RESPONSE_GAP} "
              f"— model responds uniformly (likely random weights).")
        return False

    if max_conf < SANITY_MIN_CONFIDENCE:
        print(f"  FAIL: Max confidence {max_conf:.4f} < {SANITY_MIN_CONFIDENCE} "
              f"— all outputs dead-center.")
        return False

    print("  PASS: Model shows meaningful directional response.")
    return True


def export_onnx(model: torch.nn.Module, out_path: Path) -> None:
    """Export model to a self-contained single-file ONNX (no external data)."""
    model.eval()
    dummy = torch.randn(1, 3, 224, 224)

    print(f"  Exporting to ONNX (opset 17, legacy exporter) -> {out_path} ...")
    out_path.parent.mkdir(parents=True, exist_ok=True)

    torch.onnx.export(
        model, dummy, str(out_path),
        export_params=True,
        opset_version=17,           # opset 17 — fully supported by OnnxRuntime 1.16
        do_constant_folding=True,
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={"input": {0: "batch_size"}, "output": {0: "batch_size"}},
    )

    # Attempt to merge external data into a single self-contained file
    try:
        import onnx  # type: ignore
        proto = onnx.load(str(out_path), load_external_data=True)
        onnx.save_model(proto, str(out_path), save_as_external_data=False)
        data_file = out_path.parent / (out_path.name + ".data")
        if data_file.exists():
            data_file.unlink()
            print("  Merged external data -> single-file ONNX.")
    except ImportError:
        print("  onnx package not installed - ONNX may have external data file alongside it.")
        print("  ModelManager.extractModelToCache() will handle both cases at runtime.")
    except Exception as e:
        print(f"  Warning: Could not merge external data: {e}")

    size = out_path.stat().st_size
    print(f"  Exported: {out_path} ({size:,} bytes)")


def write_checksum(model_path: Path, checksum_path: Path) -> str:
    """Compute SHA-256 of the exported ONNX and write it to a sidecar file."""
    print(f"  Computing SHA-256 ...")
    sha256 = hashlib.sha256()
    with model_path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            sha256.update(chunk)
    digest = sha256.hexdigest()
    checksum_path.write_text(f"{digest}  {model_path.name}\n", encoding="utf-8")
    print(f"  SHA-256: {digest}")
    print(f"  Written to: {checksum_path}")
    return digest


def main() -> None:
    print("DeepShield AI — EfficientNet-B0 ONNX Export with Weight Validation")
    print("=" * 65)

    # 1. Check weights file exists
    if not WEIGHTS_PATH.exists():
        print(f"\nERROR: Weights file not found: {WEIGHTS_PATH}")
        print("Run download_pretrained.py first to download the weights.")
        sys.exit(1)

    # 2. Build model
    print("\n[1/5] Building EfficientNet-B0 with 2-class head ...")
    model = build_model()

    # 3. Load weights
    print("\n[2/5] Loading pretrained weights ...")
    if not load_weights(model, WEIGHTS_PATH):
        print("\nERROR: Weight loading failed. Aborting export.")
        sys.exit(1)

    # 4. Sanity check
    print("\n[3/5] Sanity inference check ...")
    if not sanity_check(model):
        print("\nWARNING: Sanity check failed — weights may be random/incompatible.")
        answer = input("Export anyway? (y/N): ").strip().lower()
        if answer != "y":
            print("Aborted.")
            sys.exit(1)
        print("Exporting with potentially random weights (not recommended) ...")
    else:
        print("  Weights verified — proceeding with export.")

    # 5. Export ONNX
    print("\n[4/5] Exporting ONNX ...")
    export_onnx(model, ONNX_OUT)

    # 6. Checksum
    print("\n[5/5] Writing SHA-256 checksum ...")
    digest = write_checksum(ONNX_OUT, SHA256_OUT)

    print("\n" + "=" * 65)
    print("Export complete!")
    print(f"  Model : {ONNX_OUT}")
    print(f"  SHA256: {digest[:16]}...")
    print("\nNext step: Sync Android project in Android Studio.")
    print("The checksum file will be validated at runtime by ModelManager.")


if __name__ == "__main__":
    main()
