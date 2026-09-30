import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import numpy as np
from PIL import Image
import trimesh
from sculpture_pipeline import prepare, inspect_mesh, runtime_check


class SculptureTests(unittest.TestCase):
    def test_turnaround_separates_views_and_removes_key(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            pixels = np.full((30, 90, 3), (255, 0, 255), dtype=np.uint8)
            for i, color in enumerate(((80, 120, 20), (150, 60, 20), (20, 40, 180))):
                pixels[5:25, i*30+10:i*30+20] = color
            source = root / "sheet.png"
            Image.fromarray(pixels).save(source)
            result = prepare(source, root / "views")
            for name, expected in zip(("front", "side", "back"), ((80,120,20), (150,60,20), (20,40,180))):
                with Image.open(root / "views" / f"{name}.png") as view:
                    data = np.asarray(view)
                    self.assertTrue(np.any(data[:,:,3] == 0))
                    self.assertTrue(np.all(data[data[:,:,3] > 0, :3] == expected))
                    self.assertEqual(view.width, view.height)
            self.assertEqual(result['status'], 'reference-only')
            with self.assertRaises(FileExistsError):
                prepare(source, root / "views")

    def test_transformed_scene_and_budget(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "model.glb"
            scene = trimesh.Scene()
            box = trimesh.creation.box(extents=(1, 2, 1))
            scene.add_geometry(box)
            transform = np.eye(4)
            transform[0,3] = 3
            scene.add_geometry(box, transform=transform)
            scene.export(path)
            report = inspect_mesh(path, height_blocks=4)
            self.assertEqual(report['triangles'], 24)
            self.assertEqual(report['bounding_box_cells'], [128,64,32])
            self.assertEqual(report['bounding_box_host_upper_bound'], 64)
            self.assertTrue(report['review_required'])
            self.assertTrue(report['watertight'])
            with self.assertRaisesRegex(ValueError, 'Triangle budget'):
                inspect_mesh(path, max_triangles=10)
            with self.assertRaises(ValueError):
                inspect_mesh(path, height_blocks=0)

    def test_open_surface_is_reported_not_silently_repaired(self):
        with tempfile.TemporaryDirectory() as directory:
            mesh = trimesh.creation.box()
            mesh.update_faces(np.arange(len(mesh.faces)-1))
            path = Path(directory) / 'open.glb'
            mesh.export(path)
            self.assertFalse(inspect_mesh(path)['watertight'])

    def test_unsupported_platform_fails_before_gpu_import_or_download(self):
        with patch('sculpture_pipeline.platform.system', return_value='Windows'):
            with self.assertRaisesRegex(RuntimeError, 'Linux'):
                runtime_check(Path('nonexistent'))


if __name__ == '__main__':
    unittest.main()
