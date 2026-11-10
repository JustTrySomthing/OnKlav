"""Quarantine only generated Gradle transforms named by a metadata-read failure."""
from pathlib import Path
import re
import shutil
import tempfile

_PATTERN = re.compile(r"Could not read workspace metadata from ([^\r\n]+?/caches/([0-9][0-9.]*)/transforms/([a-f0-9]{32})/metadata\.bin)")


def quarantine_unreadable_transforms(log: str, gradle_home: Path) -> list[Path]:
    # Only the dependency-metadata phase is recoverable here. A compiler/test
    # failure mentioning a cache path must still stop immediately.
    if not re.search(r"Execution failed for task '[^'\r\n]*:check\w+AarMetadata'\.", log):
        return []
    home = gradle_home.resolve()
    moved = []
    seen = set()
    for match in _PATTERN.finditer(log):
        metadata = Path(match[1])
        target = home / "caches" / match[2] / "transforms" / match[3]
        if metadata != target / "metadata.bin" or target in seen:
            continue
        seen.add(target)
        if target.is_symlink() or not target.is_dir():
            continue
        if not target.resolve().is_relative_to(home) or target.resolve() != target:
            continue
        quarantine = home / "quarantined-transforms"
        if quarantine.is_symlink():
            continue
        quarantine.mkdir(exist_ok=True)
        destination = Path(tempfile.mkdtemp(prefix=match[3] + "-", dir=quarantine)) / "artifact"
        shutil.move(str(target), str(destination))
        moved.append(destination)
    return moved
