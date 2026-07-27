import re
import os
import httpx
from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse, JSONResponse
from typing import List, Optional
from starlette.middleware.base import BaseHTTPMiddleware

from models import (
    VideoCreate, VideoResponse, CategoryCreate, CategoryResponse,
    CategoryUpdate, VideoCategoryUpdate, BatchVideoIds, BatchVideoCategoryUpdate,
    SeriesCreate, SeriesUpdate, SeriesResponse,
    SeriesVideoAdd, SeriesVideoOrderUpdate,
    WatchStatusUpdate, WatchStatusResponse,
    ParentalControlConfig, ParentalControlResponse,
    WatchStatsUpdate, WatchStatsResponse,
)
from database import db

app = FastAPI(
    title="B站精选NAS服务端",
    description="家长控制视频播放系统的服务端",
    version="1.5.1"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# =========================================================================
# API Key 鉴权
# - 通过环境变量 NAS_API_KEY 配置（必填，部署时自行设置）
# - 放行：/ 、/api/health；其余 /api/* 需校验 X-API-Key
# =========================================================================
NAS_API_KEY = os.environ.get("NAS_API_KEY", "")
# 放行路径（无需鉴权）
_PUBLIC_PATHS = {"/", "/api/health"}


class ApiKeyMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        path = request.url.path
        # 仅校验 /api/* 路径，且排除公开路径
        if path.startswith("/api/") and path not in _PUBLIC_PATHS:
            provided = request.headers.get("X-API-Key", "")
            if provided != NAS_API_KEY:
                return JSONResponse(
                    status_code=401,
                    content={"detail": "无效或缺失的 API Key"}
                )
        return await call_next(request)


app.add_middleware(ApiKeyMiddleware)


@app.get("/")
def read_root():
    return {
        "status": "running",
        "service": "B站精选NAS服务端",
        "version": "1.5.1"
    }


def _video_to_response(v, watch_status=None) -> VideoResponse:
    resp = VideoResponse(
        id=v.id,
        bvid=v.bvid,
        title=v.title,
        up_name=v.up_name,
        duration=v.duration,
        category=v.category,
        cover=v.cover,
        added_at=str(v.added_at),
        added_by=v.added_by,
        series_id=getattr(v, 'series_id', ''),
        episode_index=getattr(v, 'episode_index', 0),
    )
    if watch_status:
        resp.watch_status = watch_status.status
        resp.watch_progress = watch_status.progress
        resp.last_watched_at = watch_status.last_watched_at
    return resp


# =========================================================================
# B站链接解析
# =========================================================================

def _extract_bvid(text: str) -> Optional[str]:
    """从URL或纯文本中提取BV号"""
    text = text.strip()
    # 直接是BV号
    m = re.match(r'^(BV[a-zA-Z0-9]+)$', text)
    if m:
        return m.group(1)
    # 从URL中提取
    m = re.search(r'BV([a-zA-Z0-9]+)', text)
    if m:
        return 'BV' + m.group(1)
    return None


@app.get("/api/resolve")
def resolve_bilibili_url(url: str = Query(..., description="B站视频链接或BV号")):
    """解析B站链接，返回视频信息"""
    bvid = _extract_bvid(url)
    if not bvid:
        raise HTTPException(status_code=400, detail="无法识别视频链接，请输入有效的B站视频链接或BV号")

    try:
        resp = httpx.get(
            f"https://api.bilibili.com/x/web-interface/view?bvid={bvid}",
            timeout=10,
            headers={
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            }
        )
        data = resp.json()
        if data.get("code") != 0:
            raise HTTPException(status_code=404, detail=f"视频不存在: {data.get('message', '未知错误')}")

        info = data["data"]
        return {
            "bvid": info["bvid"],
            "title": info["title"],
            "up_name": info["owner"]["name"],
            "duration": info["duration"],
            "cover": info["pic"],
        }
    except httpx.HTTPError as e:
        raise HTTPException(status_code=502, detail=f"请求B站API失败: {str(e)}")


# =========================================================================
# Videos
# =========================================================================

@app.post("/api/videos", response_model=VideoResponse)
def create_video(video: VideoCreate):
    existing = db.get_video_by_bvid(video.bvid)
    if existing:
        db.delete_video(existing.id)

    try:
        new_video = db.create_video(video)
        return _video_to_response(new_video)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"添加视频失败: {str(e)}")


@app.get("/api/videos", response_model=List[VideoResponse])
def get_videos(
    category: Optional[str] = Query(None, description="按分类过滤"),
    series_id: Optional[str] = Query(None, description="按系列过滤"),
    with_watch_status: bool = Query(True, description="是否返回观看状态"),
):
    try:
        if series_id:
            videos = db.get_series_videos(series_id)
        else:
            videos = db.get_all_videos(category=category)

        if with_watch_status and videos:
            video_ids = [v.id for v in videos]
            status_map = db.get_watch_status_batch(video_ids)
            return [_video_to_response(v, status_map.get(v.id)) for v in videos]
        return [_video_to_response(v) for v in videos]
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取视频列表失败: {str(e)}")


@app.get("/api/videos/{video_id}", response_model=VideoResponse)
def get_video(video_id: str):
    video = db.get_video(video_id)
    if not video:
        raise HTTPException(status_code=404, detail="视频不存在")
    watch_status = db.get_watch_status(video_id)
    return _video_to_response(video, watch_status)


@app.put("/api/videos/{video_id}/category", response_model=VideoResponse)
def update_video_category(video_id: str, update: VideoCategoryUpdate):
    video = db.get_video(video_id)
    if not video:
        raise HTTPException(status_code=404, detail="视频不存在")
    success = db.update_video_category(video_id, update.category)
    if not success:
        raise HTTPException(status_code=500, detail="更新分类失败")
    updated = db.get_video(video_id)
    return _video_to_response(updated)


@app.delete("/api/videos/{video_id}")
def delete_video(video_id: str):
    success = db.delete_video(video_id)
    if not success:
        raise HTTPException(status_code=404, detail="视频不存在")
    return {"message": "视频已删除", "id": video_id}


@app.delete("/api/videos/bvid/{bvid}")
def delete_video_by_bvid(bvid: str):
    existing = db.get_video_by_bvid(bvid)
    if not existing:
        raise HTTPException(status_code=404, detail="视频不存在")
    db.delete_video(existing.id)
    return {"message": "视频已删除", "bvid": bvid}


@app.post("/api/videos/batch-delete")
def batch_delete_videos(body: BatchVideoIds):
    count = db.batch_delete_videos(body.ids)
    return {"message": f"已删除 {count} 个视频", "count": count}


@app.post("/api/videos/batch-move")
def batch_move_videos(body: BatchVideoCategoryUpdate):
    count = db.batch_update_video_category(body.ids, body.category)
    return {"message": f"已移动 {count} 个视频到分类'{body.category}'", "count": count}


# =========================================================================
# Categories
# =========================================================================

@app.get("/api/categories", response_model=List[CategoryResponse])
def get_categories():
    try:
        categories = db.get_all_categories()
        return [CategoryResponse(id=c.id, name=c.name, display_order=c.display_order) for c in categories]
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取分类失败: {str(e)}")


@app.post("/api/categories", response_model=CategoryResponse)
def create_category(cat: CategoryCreate):
    existing = db.get_category_by_name(cat.name)
    if existing:
        raise HTTPException(status_code=400, detail=f"分类'{cat.name}'已存在")
    try:
        new_cat = db.create_category(cat)
        return CategoryResponse(id=new_cat.id, name=new_cat.name, display_order=new_cat.display_order)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"创建分类失败: {str(e)}")


@app.put("/api/categories/{cat_id}", response_model=CategoryResponse)
def update_category(cat_id: str, update: CategoryUpdate):
    existing = db.get_all_categories()
    cat = next((c for c in existing if c.id == cat_id), None)
    if not cat:
        raise HTTPException(status_code=404, detail="分类不存在")
    success = db.update_category(cat_id, name=update.name, display_order=update.display_order)
    if not success:
        raise HTTPException(status_code=500, detail="更新分类失败")
    updated_cats = db.get_all_categories()
    updated = next(c for c in updated_cats if c.id == cat_id)
    return CategoryResponse(id=updated.id, name=updated.name, display_order=updated.display_order)


@app.delete("/api/categories/{cat_id}")
def delete_category(cat_id: str):
    success = db.delete_category(cat_id)
    if not success:
        raise HTTPException(status_code=404, detail="分类不存在")
    return {"message": "分类已删除", "id": cat_id}


# =========================================================================
# Series 系列
# =========================================================================

@app.get("/api/series", response_model=List[SeriesResponse])
def get_all_series():
    try:
        result = db.get_all_series()
        return [
            SeriesResponse(
                id=s.id, title=s.title, cover=s.cover, description=s.description,
                display_order=s.display_order, created_at=s.created_at,
                video_count=count
            )
            for s, count in result
        ]
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取系列列表失败: {str(e)}")


@app.post("/api/series", response_model=SeriesResponse)
def create_series(series: SeriesCreate):
    try:
        new_series = db.create_series(series)
        return SeriesResponse(
            id=new_series.id, title=new_series.title, cover=new_series.cover,
            description=new_series.description, display_order=new_series.display_order,
            created_at=new_series.created_at, video_count=0
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"创建系列失败: {str(e)}")


@app.get("/api/series/{series_id}", response_model=SeriesResponse)
def get_series(series_id: str):
    series = db.get_series(series_id)
    if not series:
        raise HTTPException(status_code=404, detail="系列不存在")
    # 获取视频数
    videos = db.get_series_videos(series_id)
    return SeriesResponse(
        id=series.id, title=series.title, cover=series.cover,
        description=series.description, display_order=series.display_order,
        created_at=series.created_at, video_count=len(videos)
    )


@app.put("/api/series/{series_id}", response_model=SeriesResponse)
def update_series(series_id: str, update: SeriesUpdate):
    existing = db.get_series(series_id)
    if not existing:
        raise HTTPException(status_code=404, detail="系列不存在")
    success = db.update_series(series_id, update)
    if not success:
        raise HTTPException(status_code=500, detail="更新系列失败")
    updated = db.get_series(series_id)
    videos = db.get_series_videos(series_id)
    return SeriesResponse(
        id=updated.id, title=updated.title, cover=updated.cover,
        description=updated.description, display_order=updated.display_order,
        created_at=updated.created_at, video_count=len(videos)
    )


@app.delete("/api/series/{series_id}")
def delete_series(series_id: str):
    existing = db.get_series(series_id)
    if not existing:
        raise HTTPException(status_code=404, detail="系列不存在")
    db.delete_series(series_id)
    return {"message": "系列已删除，视频保留为未归类", "id": series_id}


@app.get("/api/series/{series_id}/videos", response_model=List[VideoResponse])
def get_series_videos(series_id: str):
    existing = db.get_series(series_id)
    if not existing:
        raise HTTPException(status_code=404, detail="系列不存在")
    videos = db.get_series_videos(series_id)
    if videos:
        video_ids = [v.id for v in videos]
        status_map = db.get_watch_status_batch(video_ids)
        return [_video_to_response(v, status_map.get(v.id)) for v in videos]
    return []


@app.post("/api/series/{series_id}/videos")
def add_videos_to_series(series_id: str, body: SeriesVideoAdd):
    existing = db.get_series(series_id)
    if not existing:
        raise HTTPException(status_code=404, detail="系列不存在")
    count = db.add_videos_to_series(series_id, body.video_ids, body.episode_index)
    return {"message": f"已添加 {count} 个视频到系列", "count": count}


@app.delete("/api/series/{series_id}/videos/{video_id}")
def remove_video_from_series(series_id: str, video_id: str):
    success = db.remove_video_from_series(series_id, video_id)
    if not success:
        raise HTTPException(status_code=404, detail="视频不在该系列中或不存在")
    return {"message": "视频已从系列移除"}


@app.put("/api/series/{series_id}/videos/order")
def update_series_video_order(series_id: str, body: SeriesVideoOrderUpdate):
    existing = db.get_series(series_id)
    if not existing:
        raise HTTPException(status_code=404, detail="系列不存在")
    count = db.update_series_video_order(body.orders)
    return {"message": f"已更新 {count} 个视频的顺序", "count": count}


# =========================================================================
# Watch History 观看记录
# =========================================================================

@app.get("/api/videos/{video_id}/watch-status", response_model=WatchStatusResponse)
def get_watch_status(video_id: str):
    status = db.get_watch_status(video_id)
    if not status:
        # 返回默认未观看状态
        return WatchStatusResponse(
            video_id=video_id, status="unwatched", progress=0.0,
            last_watched_at=None, watched_duration=0
        )
    return status


@app.post("/api/videos/{video_id}/watch-status", response_model=WatchStatusResponse)
def update_watch_status(video_id: str, update: WatchStatusUpdate):
    video = db.get_video(video_id)
    if not video:
        raise HTTPException(status_code=404, detail="视频不存在")
    if update.status not in ("unwatched", "watching", "watched"):
        raise HTTPException(status_code=400, detail="状态必须为 unwatched/watching/watched")
    return db.upsert_watch_status(video_id, update)


@app.get("/api/videos/watch-status/batch")
def get_watch_status_batch(video_ids: str = Query(..., description="逗号分隔的视频ID")):
    ids = [vid.strip() for vid in video_ids.split(",") if vid.strip()]
    if not ids:
        return {}
    return db.get_watch_status_batch(ids)


# =========================================================================
# Health
# =========================================================================

@app.get("/api/health")
def health_check():
    return {"status": "healthy"}


# =========================================================================
# Parental Control 家长控制
# =========================================================================

@app.get("/api/parental-control", response_model=ParentalControlResponse)
def get_parental_control():
    """获取家长控制配置"""
    config = db.get_parental_control()
    if not config:
        raise HTTPException(status_code=404, detail="配置不存在")
    return config


@app.post("/api/parental-control", response_model=ParentalControlResponse)
def save_parental_control(config: ParentalControlConfig):
    """保存家长控制配置"""
    try:
        result = db.save_parental_control(config.model_dump())
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"保存配置失败: {str(e)}")


# =========================================================================
# Watch Stats 每日观看统计
# =========================================================================

@app.get("/api/watch-stats", response_model=WatchStatsResponse)
def get_watch_stats(date: Optional[str] = Query(None, description="日期 YYYY-MM-DD，默认今天")):
    """获取某日观看统计"""
    from datetime import date as dt_date
    target_date = date or dt_date.today().isoformat()
    return db.get_watch_stats(target_date)


@app.post("/api/watch-stats", response_model=WatchStatsResponse)
def update_watch_stats(body: WatchStatsUpdate, date: Optional[str] = Query(None, description="日期 YYYY-MM-DD，默认今天")):
    """增量更新某日观看统计"""
    from datetime import date as dt_date
    target_date = date or dt_date.today().isoformat()
    try:
        return db.update_watch_stats(target_date, body.model_dump())
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"更新统计失败: {str(e)}")


# =========================================================================
# Web管理页面
# =========================================================================

@app.get("/admin", response_class=HTMLResponse)
def admin_page():
    return _ADMIN_HTML


_ADMIN_HTML = r"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>B站精选 - 视频管理</title>
<style>
:root {
  --primary: #fe7297;
  --primary-light: #fff0f5;
  --bg: #f5f5f5;
  --card: #fff;
  --text: #333;
  --text2: #888;
  --border: #e0e0e0;
  --danger: #e53935;
  --danger-light: #ffebee;
  --success: #43a047;
  --success-light: #e8f5e9;
}
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: var(--bg); color: var(--text); }
a { color: var(--primary); text-decoration: none; }

/* Layout */
.header { background: var(--primary); color: #fff; padding: 16px 24px; display: flex; align-items: center; justify-content: space-between; position: sticky; top: 0; z-index: 100; box-shadow: 0 2px 8px rgba(0,0,0,.15); }
.header h1 { font-size: 20px; font-weight: 600; }
.header .version { font-size: 12px; opacity: .8; margin-left: 8px; }
.container { max-width: 1200px; margin: 0 auto; padding: 20px; }

/* Tabs */
.tabs { display: flex; gap: 0; background: var(--card); border-radius: 10px; overflow: hidden; margin-bottom: 20px; box-shadow: 0 1px 4px rgba(0,0,0,.08); }
.tab { flex: 1; padding: 14px; text-align: center; cursor: pointer; font-size: 15px; font-weight: 500; border-bottom: 3px solid transparent; transition: all .2s; }
.tab:hover { background: var(--primary-light); }
.tab.active { color: var(--primary); border-bottom-color: var(--primary); background: var(--primary-light); }

/* Cards */
.card { background: var(--card); border-radius: 10px; padding: 20px; margin-bottom: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.08); }
.card-title { font-size: 16px; font-weight: 600; margin-bottom: 16px; display: flex; align-items: center; justify-content: space-between; }

/* Form */
.form-row { display: flex; gap: 12px; margin-bottom: 12px; align-items: flex-end; flex-wrap: wrap; }
.form-group { display: flex; flex-direction: column; gap: 4px; flex: 1; min-width: 200px; }
.form-group label { font-size: 13px; color: var(--text2); font-weight: 500; }
input, select { padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; font-size: 14px; outline: none; transition: border-color .2s; }
input:focus, select:focus { border-color: var(--primary); }
textarea { padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; font-size: 14px; outline: none; resize: vertical; min-height: 80px; font-family: inherit; }
textarea:focus { border-color: var(--primary); }

/* Buttons */
.btn { padding: 10px 20px; border: none; border-radius: 8px; font-size: 14px; font-weight: 500; cursor: pointer; transition: all .2s; display: inline-flex; align-items: center; gap: 6px; white-space: nowrap; }
.btn-primary { background: var(--primary); color: #fff; }
.btn-primary:hover { opacity: .9; }
.btn-danger { background: var(--danger); color: #fff; }
.btn-danger:hover { opacity: .9; }
.btn-outline { background: transparent; border: 1px solid var(--border); color: var(--text); }
.btn-outline:hover { border-color: var(--primary); color: var(--primary); }
.btn-sm { padding: 6px 12px; font-size: 13px; }
.btn:disabled { opacity: .5; cursor: not-allowed; }

/* Table */
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th { text-align: left; padding: 12px 10px; border-bottom: 2px solid var(--border); color: var(--text2); font-weight: 600; font-size: 13px; white-space: nowrap; }
td { padding: 10px; border-bottom: 1px solid var(--border); vertical-align: middle; }
tr:hover td { background: var(--primary-light); }
.video-title { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.video-cover { width: 64px; height: 40px; object-fit: cover; border-radius: 4px; background: #eee; }
.checkbox-cell { width: 40px; text-align: center; }
.checkbox-cell input { width: 16px; height: 16px; cursor: pointer; }

/* Batch bar */
.batch-bar { display: none; align-items: center; gap: 12px; padding: 12px 16px; background: var(--primary-light); border-radius: 10px; margin-bottom: 16px; font-size: 14px; }
.batch-bar.show { display: flex; }
.batch-bar .count { font-weight: 600; color: var(--primary); }

/* Category list */
.cat-list { display: flex; flex-direction: column; gap: 8px; }
.cat-item { display: flex; align-items: center; gap: 12px; padding: 12px 16px; background: var(--bg); border-radius: 8px; }
.cat-item .cat-name { font-weight: 500; flex: 1; }
.cat-item .cat-order { color: var(--text2); font-size: 13px; }
.cat-item .cat-actions { display: flex; gap: 6px; }
.cat-item input { max-width: 140px; }
.cat-item input[type=number] { max-width: 70px; }

/* Modal */
.modal-overlay { display: none; position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,.4); z-index: 200; align-items: center; justify-content: center; }
.modal-overlay.show { display: flex; }
.modal { background: var(--card); border-radius: 12px; padding: 24px; width: 90%; max-width: 480px; max-height: 80vh; overflow-y: auto; }
.modal h3 { margin-bottom: 16px; font-size: 18px; }
.modal .form-row { margin-bottom: 16px; }
.modal-actions { display: flex; gap: 10px; justify-content: flex-end; margin-top: 20px; }

/* Toast */
.toast { position: fixed; top: 80px; right: 20px; padding: 12px 20px; border-radius: 8px; color: #fff; font-size: 14px; z-index: 300; opacity: 0; transition: opacity .3s; pointer-events: none; }
.toast.show { opacity: 1; }
.toast.success { background: var(--success); }
.toast.error { background: var(--danger); }

/* Responsive */
@media (max-width: 768px) {
  .container { padding: 12px; }
  .form-row { flex-direction: column; }
  .form-group { min-width: 0; }
  .video-title { max-width: 150px; }
  .cat-item { flex-wrap: wrap; }
}

/* Filter bar */
.filter-bar { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.filter-bar select { min-width: 140px; }
.filter-bar input { min-width: 200px; }

/* Empty state */
.empty { text-align: center; padding: 40px; color: var(--text2); }
.empty .icon { font-size: 48px; margin-bottom: 12px; }

/* Resolve preview */
.resolve-preview { display: none; margin-top: 12px; padding: 16px; background: var(--success-light); border-radius: 10px; }
.resolve-preview.show { display: block; }
.resolve-preview .preview-info { display: flex; gap: 16px; align-items: flex-start; }
.resolve-preview .preview-cover { width: 120px; height: 75px; object-fit: cover; border-radius: 6px; }
.resolve-preview .preview-detail { flex: 1; }
.resolve-preview .preview-title { font-weight: 600; font-size: 15px; margin-bottom: 6px; }
.resolve-preview .preview-meta { color: var(--text2); font-size: 13px; }

/* Series list */
.series-list { display: flex; flex-direction: column; gap: 12px; }
.series-item { display: flex; align-items: center; gap: 16px; padding: 16px; background: var(--bg); border-radius: 10px; }
.series-item .series-cover { width: 80px; height: 50px; object-fit: cover; border-radius: 6px; background: #eee; }
.series-item .series-info { flex: 1; }
.series-item .series-title { font-weight: 600; font-size: 15px; margin-bottom: 4px; }
.series-item .series-meta { color: var(--text2); font-size: 13px; }
.series-item .series-actions { display: flex; gap: 6px; }

/* Watch badge */
.watch-badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 12px; font-weight: 500; }
.watch-badge.unwatched { background: #eee; color: var(--text2); }
.watch-badge.watching { background: #fff3e0; color: #e65100; }
.watch-badge.watched { background: var(--success-light); color: var(--success); }
</style>
</head>
<body>

<div class="header">
  <div><h1>B站精选 - 视频管理<span class="version">v1.5.1</span></h1></div>
  <div style="display:flex;align-items:center;gap:8px;">
    <input id="apiKeyInput" type="password" placeholder="API Key" style="padding:6px 10px;border-radius:6px;border:none;font-size:13px;width:180px;" oninput="saveApiKey()">
  </div>
</div>

<div class="container">
  <div class="tabs">
    <div class="tab active" data-tab="videos">视频管理</div>
    <div class="tab" data-tab="add">添加视频</div>
    <div class="tab" data-tab="categories">分类管理</div>
    <div class="tab" data-tab="series">系列管理</div>
  </div>

  <!-- 视频管理 -->
  <div id="tab-videos" class="tab-content">
    <div class="filter-bar">
      <select id="filterCategory"><option value="">全部分类</option></select>
      <select id="filterSeries"><option value="">全部系列</option></select>
      <input id="filterSearch" placeholder="搜索标题/BV号..." oninput="loadVideos()">
      <button class="btn btn-outline btn-sm" onclick="loadVideos()">刷新</button>
    </div>
    <div class="batch-bar" id="batchBar">
      <span>已选 <span class="count" id="selectedCount">0</span> 个视频</span>
      <select id="batchCategory"><option value="">移动到分类...</option></select>
      <button class="btn btn-primary btn-sm" onclick="batchMove()">移动</button>
      <select id="batchSeries"><option value="">加入系列...</option></select>
      <button class="btn btn-primary btn-sm" onclick="batchAddToSeries()">加入</button>
      <button class="btn btn-danger btn-sm" onclick="batchDelete()">删除</button>
      <button class="btn btn-outline btn-sm" onclick="clearSelection()">取消选择</button>
    </div>
    <div class="card">
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="checkbox-cell"><input type="checkbox" id="selectAll" onchange="toggleSelectAll()"></th>
              <th>封面</th>
              <th>标题</th>
              <th>UP主</th>
              <th>时长</th>
              <th>分类</th>
              <th>系列</th>
              <th>观看</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody id="videoTable"></tbody>
        </table>
      </div>
      <div class="empty" id="videoEmpty" style="display:none">
        <div class="icon">📺</div>
        <div>暂无视频，点击"添加视频"标签页添加</div>
      </div>
    </div>
  </div>

  <!-- 添加视频 -->
  <div id="tab-add" class="tab-content" style="display:none">
    <div class="card">
      <div class="card-title">通过链接添加视频</div>
      <div class="form-row">
        <div class="form-group" style="flex:3">
          <label>视频链接 / BV号</label>
          <input id="addUrl" placeholder="粘贴B站视频链接或BV号，如 BV1xx411c7mD">
        </div>
        <div class="form-group" style="flex:1">
          <label>分类</label>
          <select id="addCategory"><option value="">未分类</option></select>
        </div>
        <button class="btn btn-primary" onclick="resolveAndAdd()" id="resolveBtn">解析并添加</button>
      </div>
      <div class="resolve-preview" id="resolvePreview">
        <div class="preview-info">
          <img class="preview-cover" id="previewCover" src="" alt="">
          <div class="preview-detail">
            <div class="preview-title" id="previewTitle"></div>
            <div class="preview-meta" id="previewMeta"></div>
          </div>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-title">批量添加（每行一个链接或BV号）</div>
      <div class="form-row">
        <div class="form-group" style="flex:3">
          <textarea id="batchUrls" placeholder="BV1xx411c7mD&#10;https://www.bilibili.com/video/BV1xx411c7mD&#10;..."></textarea>
        </div>
        <div class="form-group" style="flex:1">
          <label>分类</label>
          <select id="batchAddCategory"><option value="">未分类</option></select>
        </div>
      </div>
      <button class="btn btn-primary" onclick="batchResolveAndAdd()" id="batchAddBtn">批量解析并添加</button>
      <div id="batchProgress" style="margin-top:12px; font-size:14px; color:var(--text2);"></div>
    </div>
  </div>

  <!-- 分类管理 -->
  <div id="tab-categories" class="tab-content" style="display:none">
    <div class="card">
      <div class="card-title">
        分类列表
        <button class="btn btn-primary btn-sm" onclick="showAddCategoryModal()">+ 新建分类</button>
      </div>
      <div class="cat-list" id="catList"></div>
      <div class="empty" id="catEmpty" style="display:none">
        <div class="icon">📁</div>
        <div>暂无分类</div>
      </div>
    </div>
  </div>

  <!-- 系列管理 -->
  <div id="tab-series" class="tab-content" style="display:none">
    <div class="card">
      <div class="card-title">
        系列列表
        <button class="btn btn-primary btn-sm" onclick="showAddSeriesModal()">+ 新建系列</button>
      </div>
      <div class="series-list" id="seriesList"></div>
      <div class="empty" id="seriesEmpty" style="display:none">
        <div class="icon">📚</div>
        <div>暂无系列，点击"新建系列"创建</div>
      </div>
    </div>
  </div>
</div>

<!-- 编辑分类弹窗 -->
<div class="modal-overlay" id="editCatModal">
  <div class="modal">
    <h3 id="catModalTitle">新建分类</h3>
    <div class="form-row">
      <div class="form-group">
        <label>分类名称</label>
        <input id="catNameInput" placeholder="输入分类名称">
      </div>
    </div>
    <div class="form-row">
      <div class="form-group">
        <label>排序（数字越小越靠前）</label>
        <input id="catOrderInput" type="number" value="0">
      </div>
    </div>
    <div class="modal-actions">
      <button class="btn btn-outline" onclick="closeCatModal()">取消</button>
      <button class="btn btn-primary" onclick="saveCategory()" id="catSaveBtn">保存</button>
    </div>
  </div>
</div>

<!-- 确认弹窗 -->
<div class="modal-overlay" id="confirmModal">
  <div class="modal">
    <h3>确认操作</h3>
    <p id="confirmMsg" style="margin:12px 0;font-size:15px;"></p>
    <div class="modal-actions">
      <button class="btn btn-outline" onclick="closeConfirm()">取消</button>
      <button class="btn btn-danger" onclick="doConfirm()" id="confirmBtn">确认</button>
    </div>
  </div>
</div>

<!-- 编辑系列弹窗 -->
<div class="modal-overlay" id="editSeriesModal">
  <div class="modal">
    <h3 id="seriesModalTitle">新建系列</h3>
    <div class="form-row">
      <div class="form-group">
        <label>系列标题</label>
        <input id="seriesTitleInput" placeholder="输入系列标题，如：数学启蒙">
      </div>
    </div>
    <div class="form-row">
      <div class="form-group">
        <label>封面URL（可选）</label>
        <input id="seriesCoverInput" placeholder="粘贴图片URL">
      </div>
    </div>
    <div class="form-row">
      <div class="form-group">
        <label>简介（可选）</label>
        <textarea id="seriesDescInput" placeholder="系列简介" style="min-height:60px;"></textarea>
      </div>
    </div>
    <div class="form-row">
      <div class="form-group">
        <label>排序（数字越小越靠前）</label>
        <input id="seriesOrderInput" type="number" value="0">
      </div>
    </div>
    <div class="modal-actions">
      <button class="btn btn-outline" onclick="closeSeriesModal()">取消</button>
      <button class="btn btn-primary" onclick="saveSeries()" id="seriesSaveBtn">保存</button>
    </div>
  </div>
</div>

<!-- 系列详情弹窗 -->
<div class="modal-overlay" id="seriesDetailModal">
  <div class="modal" style="max-width:700px;">
    <h3 id="seriesDetailTitle">系列详情</h3>
    <div id="seriesDetailInfo" style="margin-bottom:16px;color:var(--text2);font-size:14px;"></div>
    <div class="table-wrap" style="max-height:400px;overflow-y:auto;">
      <table>
        <thead>
          <tr>
            <th style="width:40px;">集</th>
            <th>标题</th>
            <th>UP主</th>
            <th>时长</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody id="seriesVideoTable"></tbody>
      </table>
    </div>
    <div class="modal-actions">
      <button class="btn btn-outline" onclick="closeSeriesDetail()">关闭</button>
    </div>
  </div>
</div>

<!-- Toast -->
<div class="toast" id="toast"></div>

<script>
const API = '';
let categories = [];
let videos = [];
let seriesList = [];
let selectedIds = new Set();
let editingCatId = null;
let editingSeriesId = null;
let confirmCallback = null;

// API Key 管理：从 localStorage 读取，输入框同步
function getApiKey() { return localStorage.getItem('nas_api_key') || ''; }
function saveApiKey() {
  localStorage.setItem('nas_api_key', document.getElementById('apiKeyInput').value);
}

async function api(path, opts = {}) {
  const headers = { 'Content-Type': 'application/json', ...opts.headers };
  const key = getApiKey();
  if (key) headers['X-API-Key'] = key;
  const res = await fetch(API + path, {
    headers,
    ...opts
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ detail: res.statusText }));
    throw new Error(err.detail || '请求失败');
  }
  return res.json();
}

// ---- Utils ----
function toast(msg, type = 'success') {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.className = 'toast ' + type + ' show';
  setTimeout(() => el.className = 'toast', 2500);
}

function playVideo(bvid) {
  window.open('https://www.bilibili.com/video/' + bvid, '_blank');
}

function fmtDuration(sec) {
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return m + ':' + String(s).padStart(2, '0');
}

// ---- Tabs ----
document.querySelectorAll('.tab').forEach(tab => {
  tab.addEventListener('click', () => {
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(c => c.style.display = 'none');
    tab.classList.add('active');
    document.getElementById('tab-' + tab.dataset.tab).style.display = '';
  });
});

// ---- Categories ----
async function loadCategories() {
  categories = await api('/api/categories');
  renderCategoryOptions();
  renderCatList();
}

function renderCategoryOptions() {
  const selects = ['filterCategory', 'addCategory', 'batchAddCategory', 'batchCategory'];
  selects.forEach(id => {
    const sel = document.getElementById(id);
    const val = sel.value;
    const isFilter = id === 'filterCategory';
    sel.innerHTML = isFilter ? '<option value="">全部分类</option>' : '<option value="">未分类</option>';
    categories.forEach(c => {
      sel.innerHTML += `<option value="${c.name}">${c.name}</option>`;
    });
    sel.value = val;
  });
}

function renderSeriesOptions() {
  const selects = ['filterSeries', 'batchSeries'];
  selects.forEach(id => {
    const sel = document.getElementById(id);
    const val = sel.value;
    const isFilter = id === 'filterSeries';
    sel.innerHTML = isFilter ? '<option value="">全部系列</option>' : '<option value="">加入系列...</option>';
    seriesList.forEach(s => {
      sel.innerHTML += `<option value="${s.id}">${esc(s.title)}</option>`;
    });
    sel.value = val;
  });
}

function renderCatList() {
  const list = document.getElementById('catList');
  const empty = document.getElementById('catEmpty');
  if (categories.length === 0) {
    list.innerHTML = '';
    empty.style.display = '';
    return;
  }
  empty.style.display = 'none';
  list.innerHTML = categories.map(c => `
    <div class="cat-item">
      <span class="cat-name">${esc(c.name)}</span>
      <span class="cat-order">排序: ${c.display_order}</span>
      <div class="cat-actions">
        <button class="btn btn-outline btn-sm" onclick="editCategory('${c.id}','${esc(c.name)}',${c.display_order})">编辑</button>
        <button class="btn btn-danger btn-sm" onclick="deleteCategory('${c.id}','${esc(c.name)}')">删除</button>
      </div>
    </div>
  `).join('');
}

function esc(s) { return s.replace(/'/g, "\\'").replace(/</g, '&lt;'); }

function showAddCategoryModal() {
  editingCatId = null;
  document.getElementById('catModalTitle').textContent = '新建分类';
  document.getElementById('catNameInput').value = '';
  document.getElementById('catOrderInput').value = categories.length;
  document.getElementById('editCatModal').classList.add('show');
}

function editCategory(id, name, order) {
  editingCatId = id;
  document.getElementById('catModalTitle').textContent = '编辑分类';
  document.getElementById('catNameInput').value = name;
  document.getElementById('catOrderInput').value = order;
  document.getElementById('editCatModal').classList.add('show');
}

function closeCatModal() {
  document.getElementById('editCatModal').classList.remove('show');
}

async function saveCategory() {
  const name = document.getElementById('catNameInput').value.trim();
  const order = parseInt(document.getElementById('catOrderInput').value) || 0;
  if (!name) { toast('请输入分类名称', 'error'); return; }

  try {
    if (editingCatId) {
      await api(`/api/categories/${editingCatId}`, {
        method: 'PUT',
        body: JSON.stringify({ name, display_order: order })
      });
      toast('分类已更新');
    } else {
      await api('/api/categories', {
        method: 'POST',
        body: JSON.stringify({ name, display_order: order })
      });
      toast('分类已创建');
    }
    closeCatModal();
    await loadCategories();
  } catch (e) {
    toast(e.message, 'error');
  }
}

function deleteCategory(id, name) {
  showConfirm(`确定删除分类"${name}"吗？该分类下的视频不会被删除，但会变为未分类。`, async () => {
    try {
      await api(`/api/categories/${id}`, { method: 'DELETE' });
      toast('分类已删除');
      await loadCategories();
      await loadVideos();
    } catch (e) { toast(e.message, 'error'); }
  });
}

// ---- Videos ----
async function loadVideos() {
  const cat = document.getElementById('filterCategory').value;
  const seriesFilter = document.getElementById('filterSeries').value;
  const search = document.getElementById('filterSearch').value.trim().toLowerCase();
  let url = '/api/videos?';
  const params = [];
  if (cat) params.push('category=' + encodeURIComponent(cat));
  if (seriesFilter) params.push('series_id=' + encodeURIComponent(seriesFilter));
  url += params.join('&');
  videos = await api(url);
  if (search) {
    videos = videos.filter(v =>
      v.title.toLowerCase().includes(search) ||
      v.bvid.toLowerCase().includes(search) ||
      v.up_name.toLowerCase().includes(search)
    );
  }
  renderVideos();
}

function renderVideos() {
  const tbody = document.getElementById('videoTable');
  const empty = document.getElementById('videoEmpty');
  if (videos.length === 0) {
    tbody.innerHTML = '';
    empty.style.display = '';
    return;
  }
  empty.style.display = 'none';
  tbody.innerHTML = videos.map(v => {
    // 观看状态标签
    let watchBadge = '<span class="watch-badge unwatched">未看</span>';
    if (v.watch_status === 'watching') {
      watchBadge = `<span class="watch-badge watching">在看 ${Math.round(v.watch_progress*100)}%</span>`;
    } else if (v.watch_status === 'watched') {
      watchBadge = '<span class="watch-badge watched">已看</span>';
    }
    // 系列名称
    let seriesName = '<span style="color:var(--text2)">-</span>';
    if (v.series_id) {
      const s = seriesList.find(x => x.id === v.series_id);
      if (s) seriesName = `第${v.episode_index+1}集`;
    }
    return `
    <tr>
      <td class="checkbox-cell"><input type="checkbox" data-id="${v.id}" ${selectedIds.has(v.id)?'checked':''} onchange="toggleSelect('${v.id}')"></td>
      <td>${v.cover ? `<img class="video-cover" src="${v.cover}" loading="lazy" onerror="this.style.display='none'">` : ''}</td>
      <td class="video-title" title="${esc(v.title)}">${esc(v.title)}</td>
      <td>${esc(v.up_name)}</td>
      <td>${fmtDuration(v.duration)}</td>
      <td>${esc(v.category) || '<span style="color:var(--text2)">未分类</span>'}</td>
      <td>${seriesName}</td>
      <td>${watchBadge}</td>
      <td>
        <button class="btn btn-primary btn-sm" onclick="playVideo('${v.bvid}')">播放</button>
        <button class="btn btn-outline btn-sm" onclick="moveVideo('${v.id}')">分类</button>
        <button class="btn btn-outline btn-sm" onclick="moveVideoSeries('${v.id}')">系列</button>
        <button class="btn btn-danger btn-sm" onclick="deleteVideo('${v.id}','${esc(v.title)}')">删除</button>
      </td>
    </tr>
  `}).join('');
  updateBatchBar();
}

function toggleSelect(id) {
  if (selectedIds.has(id)) selectedIds.delete(id);
  else selectedIds.add(id);
  updateBatchBar();
}

function toggleSelectAll() {
  const checked = document.getElementById('selectAll').checked;
  if (checked) videos.forEach(v => selectedIds.add(v.id));
  else selectedIds.clear();
  renderVideos();
}

function clearSelection() {
  selectedIds.clear();
  document.getElementById('selectAll').checked = false;
  renderVideos();
}

function updateBatchBar() {
  const bar = document.getElementById('batchBar');
  document.getElementById('selectedCount').textContent = selectedIds.size;
  if (selectedIds.size > 0) bar.classList.add('show');
  else bar.classList.remove('show');
}

function moveVideo(id) {
  const video = videos.find(v => v.id === id);
  if (!video) return;
  const catName = prompt(`移动"${video.title}"到分类：\n（留空表示未分类）`, video.category);
  if (catName === null) return;
  api(`/api/videos/${id}/category`, {
    method: 'PUT',
    body: JSON.stringify({ category: catName })
  }).then(() => {
    toast('已移动');
    loadVideos();
  }).catch(e => toast(e.message, 'error'));
}

function deleteVideo(id, title) {
  showConfirm(`确定删除视频"${title}"吗？`, async () => {
    try {
      await api(`/api/videos/${id}`, { method: 'DELETE' });
      toast('视频已删除');
      selectedIds.delete(id);
      await loadVideos();
    } catch (e) { toast(e.message, 'error'); }
  });
}

async function batchDelete() {
  if (selectedIds.size === 0) return;
  showConfirm(`确定删除选中的 ${selectedIds.size} 个视频吗？`, async () => {
    try {
      await api('/api/videos/batch-delete', {
        method: 'POST',
        body: JSON.stringify({ ids: [...selectedIds] })
      });
      toast('批量删除成功');
      selectedIds.clear();
      await loadVideos();
    } catch (e) { toast(e.message, 'error'); }
  });
}

async function batchMove() {
  const cat = document.getElementById('batchCategory').value;
  if (!cat) { toast('请选择目标分类', 'error'); return; }
  if (selectedIds.size === 0) return;
  try {
    await api('/api/videos/batch-move', {
      method: 'POST',
      body: JSON.stringify({ ids: [...selectedIds], category: cat })
    });
    toast(`已移动 ${selectedIds.size} 个视频到"${cat}"`);
    selectedIds.clear();
    await loadVideos();
  } catch (e) { toast(e.message, 'error'); }
}

async function batchAddToSeries() {
  const sid = document.getElementById('batchSeries').value;
  if (!sid) { toast('请选择目标系列', 'error'); return; }
  if (selectedIds.size === 0) return;
  try {
    const res = await api(`/api/series/${sid}/videos`, {
      method: 'POST',
      body: JSON.stringify({ video_ids: [...selectedIds] })
    });
    toast(res.message);
    selectedIds.clear();
    await loadVideos();
    await loadSeries();
  } catch (e) { toast(e.message, 'error'); }
}

function moveVideoSeries(id) {
  const video = videos.find(v => v.id === id);
  if (!video) return;
  const options = seriesList.map(s => {
    const sel = s.id === video.series_id ? 'selected' : '';
    return `<option value="${s.id}" ${sel}>${esc(s.title)}</option>`;
  }).join('');
  const html = `<select id="seriesSelectModal" style="width:100%;padding:8px;margin:8px 0;">
    <option value="">不加入任何系列</option>
    ${options}
  </select>`;
  showConfirm(html, async () => {
    const newSid = document.getElementById('seriesSelectModal').value;
    try {
      // 如果原来有系列且换了系列，先从原系列移除
      if (video.series_id && video.series_id !== newSid) {
        await api(`/api/series/${video.series_id}/videos/${id}`, { method: 'DELETE' });
      }
      if (newSid) {
        await api(`/api/series/${newSid}/videos`, {
          method: 'POST',
          body: JSON.stringify({ video_ids: [id] })
        });
      }
      toast('系列已更新');
      await loadVideos();
      await loadSeries();
    } catch (e) { toast(e.message, 'error'); }
  });
}

// ---- Add by URL ----
async function resolveAndAdd() {
  const url = document.getElementById('addUrl').value.trim();
  if (!url) { toast('请输入视频链接', 'error'); return; }
  const cat = document.getElementById('addCategory').value;
  const btn = document.getElementById('resolveBtn');
  btn.disabled = true;
  btn.textContent = '解析中...';
  const preview = document.getElementById('resolvePreview');
  preview.classList.remove('show');

  try {
    const info = await api(`/api/resolve?url=${encodeURIComponent(url)}`);
    document.getElementById('previewCover').src = info.cover || '';
    document.getElementById('previewTitle').textContent = info.title;
    document.getElementById('previewMeta').textContent = `UP: ${info.up_name} | 时长: ${fmtDuration(info.duration)} | ${info.bvid}`;
    preview.classList.add('show');

    await api('/api/videos', {
      method: 'POST',
      body: JSON.stringify({
        bvid: info.bvid,
        title: info.title,
        up_name: info.up_name,
        duration: info.duration,
        cover: info.cover || '',
        category: cat,
        added_by: 'Web管理页'
      })
    });
    toast('视频添加成功');
    document.getElementById('addUrl').value = '';
    loadVideos();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    btn.disabled = false;
    btn.textContent = '解析并添加';
  }
}

async function batchResolveAndAdd() {
  const text = document.getElementById('batchUrls').value.trim();
  if (!text) { toast('请输入视频链接', 'error'); return; }
  const cat = document.getElementById('batchAddCategory').value;
  const btn = document.getElementById('batchAddBtn');
  const progress = document.getElementById('batchProgress');
  btn.disabled = true;

  const lines = text.split('\n').map(l => l.trim()).filter(l => l);
  let success = 0, fail = 0;

  for (let i = 0; i < lines.length; i++) {
    progress.textContent = `正在处理 ${i + 1}/${lines.length}...`;
    try {
      const info = await api(`/api/resolve?url=${encodeURIComponent(lines[i])}`);
      await api('/api/videos', {
        method: 'POST',
        body: JSON.stringify({
          bvid: info.bvid, title: info.title, up_name: info.up_name,
          duration: info.duration, cover: info.cover || '',
          category: cat, added_by: 'Web管理页'
        })
      });
      success++;
    } catch (e) {
      fail++;
    }
  }

  progress.textContent = `完成！成功 ${success} 个，失败 ${fail} 个`;
  toast(`批量添加完成：成功${success}，失败${fail}`);
  btn.disabled = false;
  if (success > 0) {
    document.getElementById('batchUrls').value = '';
    loadVideos();
  }
}

// ---- Confirm ----
function showConfirm(msg, callback) {
  document.getElementById('confirmMsg').innerHTML = msg;
  confirmCallback = callback;
  document.getElementById('confirmModal').classList.add('show');
}
function closeConfirm() {
  document.getElementById('confirmModal').classList.remove('show');
  confirmCallback = null;
}
async function doConfirm() {
  const cb = confirmCallback;
  closeConfirm();
  if (cb) await cb();
}

// ---- Series ----
async function loadSeries() {
  try {
    seriesList = await api('/api/series');
    renderSeriesOptions();
    renderSeriesList();
  } catch (e) { toast(e.message, 'error'); }
}

function renderSeriesList() {
  const list = document.getElementById('seriesList');
  const empty = document.getElementById('seriesEmpty');
  if (seriesList.length === 0) {
    list.innerHTML = '';
    empty.style.display = '';
    return;
  }
  empty.style.display = 'none';
  list.innerHTML = seriesList.map(s => `
    <div class="series-item">
      ${s.cover ? `<img class="series-cover" src="${s.cover}" onerror="this.style.display='none'">` : '<div class="series-cover" style="display:flex;align-items:center;justify-content:center;color:var(--text2);">📚</div>'}
      <div class="series-info">
        <div class="series-title">${esc(s.title)}</div>
        <div class="series-meta">${s.video_count} 集 ${s.description ? '· ' + esc(s.description) : ''}</div>
      </div>
      <div class="series-actions">
        <button class="btn btn-outline btn-sm" onclick="viewSeriesDetail('${s.id}')">详情</button>
        <button class="btn btn-outline btn-sm" onclick="editSeries('${s.id}')">编辑</button>
        <button class="btn btn-danger btn-sm" onclick="deleteSeries('${s.id}','${esc(s.title)}')">删除</button>
      </div>
    </div>
  `).join('');
}

function showAddSeriesModal() {
  editingSeriesId = null;
  document.getElementById('seriesModalTitle').textContent = '新建系列';
  document.getElementById('seriesTitleInput').value = '';
  document.getElementById('seriesCoverInput').value = '';
  document.getElementById('seriesDescInput').value = '';
  document.getElementById('seriesOrderInput').value = seriesList.length;
  document.getElementById('editSeriesModal').classList.add('show');
}

function editSeries(id) {
  const s = seriesList.find(x => x.id === id);
  if (!s) return;
  editingSeriesId = id;
  document.getElementById('seriesModalTitle').textContent = '编辑系列';
  document.getElementById('seriesTitleInput').value = s.title;
  document.getElementById('seriesCoverInput').value = s.cover || '';
  document.getElementById('seriesDescInput').value = s.description || '';
  document.getElementById('seriesOrderInput').value = s.display_order;
  document.getElementById('editSeriesModal').classList.add('show');
}

function closeSeriesModal() {
  document.getElementById('editSeriesModal').classList.remove('show');
}

async function saveSeries() {
  const title = document.getElementById('seriesTitleInput').value.trim();
  const cover = document.getElementById('seriesCoverInput').value.trim();
  const description = document.getElementById('seriesDescInput').value.trim();
  const order = parseInt(document.getElementById('seriesOrderInput').value) || 0;
  if (!title) { toast('请输入系列标题', 'error'); return; }

  try {
    if (editingSeriesId) {
      await api(`/api/series/${editingSeriesId}`, {
        method: 'PUT',
        body: JSON.stringify({ title, cover, description, display_order: order })
      });
      toast('系列已更新');
    } else {
      await api('/api/series', {
        method: 'POST',
        body: JSON.stringify({ title, cover, description, display_order: order })
      });
      toast('系列已创建');
    }
    closeSeriesModal();
    await loadSeries();
  } catch (e) { toast(e.message, 'error'); }
}

function deleteSeries(id, title) {
  showConfirm(`确定删除系列"${title}"吗？系列内的视频不会被删除，但会变为未归类。`, async () => {
    try {
      await api(`/api/series/${id}`, { method: 'DELETE' });
      toast('系列已删除');
      await loadSeries();
      await loadVideos();
    } catch (e) { toast(e.message, 'error'); }
  });
}

async function viewSeriesDetail(id) {
  const s = seriesList.find(x => x.id === id);
  if (!s) return;
  document.getElementById('seriesDetailTitle').textContent = s.title;
  document.getElementById('seriesDetailInfo').textContent = `${s.video_count} 集 · ${s.description || '无简介'}`;
  document.getElementById('seriesDetailModal').classList.add('show');
  try {
    const vids = await api(`/api/series/${id}/videos`);
    const tbody = document.getElementById('seriesVideoTable');
    if (vids.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--text2);padding:20px;">暂无视频，请在视频管理中加入</td></tr>';
    } else {
      tbody.innerHTML = vids.map(v => `
        <tr>
          <td>${v.episode_index + 1}</td>
          <td class="video-title" title="${esc(v.title)}">${esc(v.title)}</td>
          <td>${esc(v.up_name)}</td>
          <td>${fmtDuration(v.duration)}</td>
          <td>
            <button class="btn btn-primary btn-sm" onclick="playVideo('${v.bvid}')">播放</button>
            <button class="btn btn-danger btn-sm" onclick="removeFromSeries('${id}','${v.id}','${esc(v.title)}')">移除</button>
          </td>
        </tr>
      `).join('');
    }
  } catch (e) { toast(e.message, 'error'); }
}

function closeSeriesDetail() {
  document.getElementById('seriesDetailModal').classList.remove('show');
}

function removeFromSeries(seriesId, videoId, title) {
  showConfirm(`确定从系列中移除"${title}"吗？视频不会被删除。`, async () => {
    try {
      await api(`/api/series/${seriesId}/videos/${videoId}`, { method: 'DELETE' });
      toast('已从系列移除');
      await viewSeriesDetail(seriesId);
      await loadSeries();
      await loadVideos();
    } catch (e) { toast(e.message, 'error'); }
  });
}

// ---- Init ----
async function init() {
  // 回填已保存的 API Key
  document.getElementById('apiKeyInput').value = getApiKey();
  await loadCategories();
  await loadSeries();
  await loadVideos();
}
init();
</script>
</body>
</html>"""
