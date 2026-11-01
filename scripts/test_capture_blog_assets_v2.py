import re
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name('capture_blog_assets_v2.py')


class BlogCaptureContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = SCRIPT.read_text(encoding='utf-8')

    def test_current_version_and_every_primary_subtab(self):
        self.assertIn("version: '1.8.17'", self.text)
        for name in (
            '按键映射手势', '接管模式图解', '360全景联动', '车速与大灯', '网络与外设',
            '换挡语音', '驾驶模式', '车门分门播报', '电动尾门', '音乐音源优先级', '专属语音包',
            '实时油价日历', '智能加油账本', '专车精选商城', '特权安装通道', '整车应用管理',
            '桌面胶囊悬浮', '顶栏状态胶囊', '系统维护与工具', '外观主题风格', '车标与启动项',
        ):
            self.assertIn(name, self.text, name)

    def test_new_features_and_uploaded_log_state(self):
        for name in ('preview_wheel_mode.webp', 'preview_wheel_learning.webp',
                     'preview_body_trunk.webp', 'preview_system_capsules.webp',
                     'preview_system_icon.webp', 'preview_log_modal.webp',
                     'preview_widget.webp'):
            self.assertIn(name, self.text, name)
        self.assertIn('onWheelKeyLearned', self.text)
        self.assertIn('onLogUploadFinished', self.text)
        self.assertIn('onLogcatDumpFinished', self.text)

    def test_browser_lifecycle_is_scoped(self):
        self.assertNotIn('pkill', self.text)
        self.assertIn('atexit.register', self.text)
        self.assertIn('proc.wait', self.text)
        self.assertIn('proc.terminate', self.text)
        self.assertIn('proc.kill', self.text)

    def test_outputs_compressed_webp_and_have_assertions(self):
        self.assertNotRegex(self.text, r"capture_rect\([^\n]+\.png")
        self.assertIn("Image.Resampling.LANCZOS", self.text)
        self.assertIn('assert ', self.text)
        self.assertIn('expected_text', self.text)
        self.assertIn('manifest.json', self.text)
        self.assertIn("height:r.height>600?r.bottom:720", self.text)
        self.assertIn("height = max(720, min(1900, bounds['bottom'] + 24))", self.text)
        self.assertNotIn("if (aside) bottom", self.text)


if __name__ == '__main__':
    unittest.main()
