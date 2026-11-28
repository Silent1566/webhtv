# -*- coding: utf-8 -*-
"""版本自增脚本：versionCode +1，versionName 末位 +1，并同步 UPDATE_NOTES.md 版本标题。

用法：
    python bump_version.py            # 递增版本
    python bump_version.py --dry-run  # 只预览，不写入
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GRADLE = ROOT / "app" / "build.gradle"
NOTES = ROOT / "UPDATE_NOTES.md"
DRY_RUN = "--dry-run" in sys.argv

text = GRADLE.read_text(encoding="utf-8")
m_code = re.search(r"(\bversionCode\s+)(\d+)", text)
m_name = re.search(r'(\bversionName\s+")(\d+\.\d+\.\d+)(")', text)
if not m_code or not m_name:
    raise SystemExit("无法在 app/build.gradle 中定位 versionCode / versionName")

old_code = int(m_code.group(2))
old_name = m_name.group(2)
parts = old_name.split(".")
if len(parts) != 3 or not all(p.isdigit() for p in parts):
    raise SystemExit("versionName 格式需为 x.y.z，实际为: %s" % old_name)
new_code = old_code + 1
new_name = "%s.%s.%d" % (parts[0], parts[1], int(parts[2]) + 1)

print("versionCode: %d -> %d" % (old_code, new_code))
print("versionName: %s -> %s" % (old_name, new_name))
if DRY_RUN:
    print("[dry-run] 未写入任何文件")
    raise SystemExit(0)

# 写回 build.gradle（只替换第一处，保留其余格式）
text = text[:m_code.start(2)] + str(new_code) + text[m_code.end(2):]
m_name = re.search(r'(\bversionName\s+")(\d+\.\d+\.\d+)(")', text)
text = text[:m_name.start(2)] + new_name + text[m_name.end(2):]
GRADLE.write_text(text, encoding="utf-8")
print("已更新:", GRADLE)

# 同步 UPDATE_NOTES.md 首个 ## vX.Y.Z（versionCode N）标题
if NOTES.exists():
    notes = NOTES.read_text(encoding="utf-8")
    heading = re.search(r"(## v%s（versionCode %d）)" % (re.escape(old_name), old_code), notes)
    if heading:
        new_heading = "## v%s（versionCode %d）" % (new_name, new_code)
        notes = notes[:heading.start(1)] + new_heading + notes[heading.end(1):]
        NOTES.write_text(notes, encoding="utf-8")
        print("已更新:", NOTES)
    else:
        print("提示: UPDATE_NOTES.md 未找到匹配的旧版本标题，请手动补 v%s 段落" % new_name)
