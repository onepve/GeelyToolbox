#!/usr/bin/env python3
"""Regression tests for the touch-button focus CSS guard."""
import unittest
from check_button_focus import has_touch_focus_guard

class ButtonFocusGuardTests(unittest.TestCase):
    def test_global_guard(self):
        self.assertTrue(has_touch_focus_guard('<style>*:focus {outline:none !important}</style>'))
    def test_button_guard(self):
        self.assertTrue(has_touch_focus_guard('<style>button:focus { outline: 0 !important; }</style>'))
    def test_missing_fix_is_rejected(self):
        self.assertFalse(has_touch_focus_guard('<style>button { color:white }</style>'))
    def test_comment_is_not_a_fix(self):
        self.assertFalse(has_touch_focus_guard('<style>/* *:focus {outline:none !important} */</style>'))
    def test_scoped_rule_is_not_global(self):
        self.assertFalse(has_touch_focus_guard('<style scoped>*:focus {outline:none !important}</style>'))
    def test_unsupported_selector_group_is_rejected(self):
        self.assertFalse(has_touch_focus_guard('<style>button:focus,button:focus-visible {outline:none !important}</style>'))
    def test_visible_outline_is_rejected(self):
        self.assertFalse(has_touch_focus_guard('<style>*:focus {outline:1px dotted red !important}</style>'))
    def test_later_override_is_rejected(self):
        self.assertFalse(has_touch_focus_guard('<style>*:focus {outline:none !important;outline:2px dotted red !important}</style>'))

if __name__ == '__main__':
    unittest.main()
