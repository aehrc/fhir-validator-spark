"""
Tests that the built distributions carry the licence, which the Apache License 2.0 requires.

Author: John Grimes
"""

import shutil
import subprocess
import tarfile
import zipfile
from pathlib import Path

import pytest

PYTHON = Path(__file__).resolve().parents[1]
LICENSE = (PYTHON.parent / "LICENSE").read_text()


@pytest.fixture(scope="module")
def dist(tmp_path_factory):
    """Builds the sdist and the wheel."""
    out = tmp_path_factory.mktemp("dist")
    subprocess.run(
        [shutil.which("uv"), "build", "--quiet", "--out-dir", str(out)],
        cwd=PYTHON,
        check=True,
    )
    return out


def test_wheel_contains_the_licence(dist):
    (wheel,) = dist.glob("*.whl")
    with zipfile.ZipFile(wheel) as archive:
        (name,) = [
            n for n in archive.namelist() if n.endswith(".dist-info/licenses/LICENSE")
        ]
        assert archive.read(name).decode() == LICENSE


def test_sdist_contains_the_licence(dist):
    (sdist,) = dist.glob("*.tar.gz")
    with tarfile.open(sdist) as archive:
        (member,) = [m for m in archive.getmembers() if m.name.endswith("/LICENSE")]
        assert archive.extractfile(member).read().decode() == LICENSE
