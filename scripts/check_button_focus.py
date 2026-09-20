#!/usr/bin/env python3
"""Conservative static contract for Chromium 68 touch focus CSS.

This checks the supported global guard, not the complete browser cascade.
Unknown selector groups are rejected: old Chromium drops an entire selector
list when one selector (such as :focus-visible) is unsupported.
"""
import re
from pathlib import Path


def has_touch_focus_guard(document):
    for attrs, css in re.findall(r'<style\b([^>]*)>(.*?)</style>', document, re.S | re.I):
        if re.search(r'\bscoped\b', attrs, re.I):
            continue
        css = re.sub(r'/\*.*?\*/', '', css, flags=re.S)
        for selectors, body in re.findall(r'([^{}]+)\{([^{}]*)\}', css):
            selectors = [s.strip() for s in selectors.split(',')]
            if not selectors or not all(s in ('*:focus', ':focus', 'button:focus') for s in selectors):
                continue
            declarations = re.findall(r'(?:^|;)\s*outline\s*:\s*([^;]+)', body, re.I)
            if declarations and re.fullmatch(r'(?:none|0(?:px)?)\s*!important\s*', declarations[-1], re.I):
                return True
    return False


def check(root):
    targets = ['web/src/App.vue', 'app/src/main/assets/toolbox_ui.html']
    return [f'{path}: 缺少 Chromium 68 可解析的全局 focus outline 禁用规则'
            for path in targets if not has_touch_focus_guard((Path(root) / path).read_text())]
