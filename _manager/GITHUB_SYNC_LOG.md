# GitHub 同步记录

本文件只追加记录每次由用户明确授权的 GitHub 同步尝试。成功、失败和部分完成必须分别写明；没有远端证据时不得标记成功。

## 2026-09-16 — 首次上传尝试 001

### 授权范围

- 用户明确授权：是。
- 远端：`zzf122561-bot/English-learning-app`。
- 传输地址：`git@github.com:zzf122561-bot/English-learning-app.git`。
- 主分支：本地 `refs/heads/master` → 远端 `refs/heads/main`。
- 授权主分支提交：`1701d5f76c49a8faca1904c0940c68bf2fa31e9e`。
- 授权标签：`app-v0.3.0`、`feature-01-v0.1.0-user-tested`、`feature-01-v0.2.0-font-sizing`、`feature-01-v0.2.0-user-tested`、`feature-01-v0.2.0-user-verified`。
- 强制推送、删除、镜像、批量分支：均未授权。

### 前置证据

- GitHub插件确认仓库存在、公开、size为0、默认分支为`main`，当前插件连接具有pull和push权限。
- 同步任务确认工作树干净。
- 本地`refs/heads/master`与授权SHA一致。
- 五个标签的直接对象SHA逐一与总控授权一致。
- Git跟踪文件扫描未发现APK、AAB、JKS、keystore、MDX、MDD、数据库或`local.properties`候选。

### 执行结果

- 状态：`blocked_before_push`。
- 同步任务先执行SSH只读远端核验，并为本次授权申请网络权限后重试。
- 两次均失败于：`Could not resolve hostname github.com: Temporary failure in name resolution`。
- 连接没有进入SSH认证阶段，因此没有判定SSH密钥是否有效。
- 未配置`origin`，未执行fetch、pull或push。
- 远端没有本任务造成的分支、标签或部分内容。
- 本地文件、配置和Git历史未被同步任务修改。

### 授权终止

- 本次授权随失败回报结束。
- 网络恢复后不得自动补传；再次尝试必须取得用户新的明确授权，并按当时最新本地`master`重新固定SHA。
- 不得改用GitHub内容API逐文件重建仓库历史。

## 2026-09-16 — 首次上传重试 002

### 授权范围

- 用户再次明确授权重试，并确认GitHub连接器已经连接。
- GitHub连接器重新确认仓库存在、公开、size为0、默认分支为`main`、分支列表为空，当前连接具有push权限。
- 本地 `refs/heads/master` → 远端 `refs/heads/main`。
- 本次固定的本地主分支提交：`828313db1a8dc694de53872b13b4f73af0a1d84b`。
- 授权标签仍为attempt 001列出的五个精确标签；未授权功能分支、强推、删除或镜像。

### 执行结果

- 状态：`blocked_before_push`。
- 同步任务重新确认工作树干净、`master`与授权SHA一致、五个标签直接对象SHA一致、当前仍未配置remote。
- 同步任务申请网络权限后，用SSH地址执行只读`ls-remote`。
- 仍失败于：`Could not resolve hostname github.com: Temporary failure in name resolution`。
- 连接没有进入SSH认证；连接器成功不等于本地Git终端的DNS和SSH链路可用。
- 未配置`origin`，未fetch、pull或push；远端没有分支、标签或部分上传；本地无同步任务造成的变更。

### 授权终止

- 本次重试授权随失败回报结束。
- 后续重试仍需用户新的明确授权，并以届时最新本地`master`重新固定SHA。

## 2026-09-16 — 首次上传成功 003

### 授权范围

- 用户在专用GitHub同步任务中直接明确授权，把本地`master`推送到公开仓库`zzf122561-bot/English-learning-app`的`main`，并逐个推送五个既有历史标签。
- 授权的主分支提交：`828313db1a8dc694de53872b13b4f73af0a1d84b`。
- 未授权功能分支、未提交文件、强推或删除远端引用。

### 执行结果

- 状态：`success`；使用标准Git普通非强制push，无部分失败。
- 远端`refs/heads/main`经只读复核为`828313db1a8dc694de53872b13b4f73af0a1d84b`。
- 远端`refs/tags/app-v0.3.0`直接对象SHA：`22ad46b4f9e97aaf100a8fb395092f1fc6f2bff2`。
- 远端`refs/tags/feature-01-v0.1.0-user-tested`直接对象SHA：`1bfcb294ef7ef5461d981410cc70dd5a61f46d41`。
- 远端`refs/tags/feature-01-v0.2.0-font-sizing`直接对象SHA：`e920eae7b377449e53945776c06d73381b18a366`。
- 远端`refs/tags/feature-01-v0.2.0-user-tested`直接对象SHA：`e51c17344cd88189ea75b35783fc4741234d04fd`。
- 远端`refs/tags/feature-01-v0.2.0-user-verified`直接对象SHA：`4ddb33d2217bfd8c0c893759239ea40024136b10`。
- 未推送功能分支、APK、用户词典、签名密钥或未提交改动；`origin`保持登记的HTTPS地址。

### 后续分支范围

- 用户随后要求同步所有本地分支：`master`、`codex/1`和`codex/feature-02-dictionary`。
- 功能分支工作树干净，提交分别为`207e308bb3d3106648eb82550698e1eb98d2baff`与`de23797a29fa2c0c3927ce376bca6c4f8c3a65d5`，均已被本地`master`包含。
- 2026-09-16只读预检确认远端功能分支尚不存在；公开推送等待用户在专用同步任务中直接确认，不得把本条记录视为授权。
