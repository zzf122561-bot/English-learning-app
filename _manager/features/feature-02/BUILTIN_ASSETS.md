# 功能2内置词典资产登记

更新时间：2026-08-24

## 权限与存储边界

- 只读来源：`D:\Codex_Project\Codex_EnglishApp\dictionary-users`。
- 原始 MDX/MDD、生成的 `app/src/main/assets/dictionaries/` 和最终 APK 均由 Git 忽略，不提交、不上传。
- `_manager/tools/prepare-dictionary-assets.ps1` 每次构建先核对源文件 SHA-256，再复制并复核；任一不一致立即停止，不修改用户原文件。
- 生成采用同一资产目录内的唯一暂存目录和原子替换；重复运行不会遗留 staging/backup 目录或覆盖未校验内容。

## 固定资产

| 稳定 ID | 文件 | 字节 | SHA-256 |
|---|---|---:|---|
| `builtin.collins-advanced-bilingual` | `柯林斯高阶英汉双解学习词典（好看）.mdx` | 13,887,505 | `14843F7E443FB1CDACC94145C9AE68879A582F13FA68399D21CAA5768A704C10` |
| `builtin.collins-advanced-bilingual` | `柯林斯高阶英汉双解学习词典（好看）.mdd` | 15,307 | `15A1668EAB08DBAD960C942CB99A7842AC8C7B77736A1936A4E77FD00FE6ED6C` |
| `builtin.oxford-ald9-en-en` | `Oxford ALD_9th_En-En.mdx` | 41,514,928 | `8E140C288D9F8D195F707F1297ADD0233E9CB44C31DC0BBCFEB46063C99C24BE` |
| `builtin.oxford-ald9-en-en` | `Oxford ALD_9th_En-En.mdd` | 4,104,095 | `4ED26627CD7FC26916CA23CF9A3AF44FD03710EECFCDDC8607B2717C1AAF1BAE` |

## 生成清单

- App 内路径：`assets/dictionaries/manifest.json`。
- schema：`schemaVersion=1`。
- 词典顺序：柯林斯双解 → Oxford ALD 9。
- 当前确定性清单 SHA-256：`30A2753EDC3E8B2F4E93BFD62120E9CAD3C7B99720732BB9D191F6825BBD0921`。
- 已连续运行两次生成脚本并得到相同清单哈希；资产根下没有遗留 staging/backup 目录。

该清单哈希只证明构建输入清单一致；APK 的最终 SHA-256 和签名证书必须在干净提交构建后另行登记。
