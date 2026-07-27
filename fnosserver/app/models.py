from pydantic import BaseModel
from typing import Optional, List


class VideoBase(BaseModel):
    bvid: str
    title: str
    up_name: str
    duration: int
    category: str = ""
    cover: str = ""


class VideoCreate(VideoBase):
    added_by: Optional[str] = "未知设备"


class Video(VideoBase):
    id: str
    added_at: str
    added_by: str
    series_id: str = ""
    episode_index: int = 0

    class Config:
        from_attributes = True


class VideoResponse(BaseModel):
    id: str
    bvid: str
    title: str
    up_name: str
    duration: int
    category: str
    cover: str = ""
    added_at: str
    added_by: str
    series_id: str = ""
    episode_index: int = 0
    # 观看状态（可选，列表接口填充）
    watch_status: Optional[str] = None
    watch_progress: float = 0.0
    last_watched_at: Optional[str] = None


class CategoryBase(BaseModel):
    name: str
    display_order: int = 0


class CategoryCreate(CategoryBase):
    pass


class Category(CategoryBase):
    id: str

    class Config:
        from_attributes = True


class CategoryUpdate(BaseModel):
    name: Optional[str] = None
    display_order: Optional[int] = None


class CategoryResponse(CategoryBase):
    id: str


class VideoCategoryUpdate(BaseModel):
    category: str


class BatchVideoIds(BaseModel):
    ids: List[str]


class BatchVideoCategoryUpdate(BaseModel):
    ids: List[str]
    category: str


# =========================================================================
# Series 系列
# =========================================================================

class SeriesBase(BaseModel):
    title: str
    cover: str = ""
    description: str = ""
    display_order: int = 0


class SeriesCreate(SeriesBase):
    pass


class SeriesUpdate(BaseModel):
    title: Optional[str] = None
    cover: Optional[str] = None
    description: Optional[str] = None
    display_order: Optional[int] = None


class Series(SeriesBase):
    id: str
    created_at: str

    class Config:
        from_attributes = True


class SeriesResponse(SeriesBase):
    id: str
    created_at: str
    video_count: int = 0


class SeriesVideoAdd(BaseModel):
    """添加视频到系列"""
    video_ids: List[str]
    episode_index: Optional[int] = None  # 不指定则自动追加到末尾


class SeriesVideoOrderUpdate(BaseModel):
    """调整系列内视频顺序"""
    orders: List[dict]  # [{"video_id": "xxx", "episode_index": 0}, ...]


# =========================================================================
# Watch History 观看记录
# =========================================================================

class WatchStatusUpdate(BaseModel):
    status: str  # unwatched / watching / watched
    progress: float = 0.0  # 0.0 - 1.0
    watched_duration: int = 0  # 累计观看秒数


class WatchStatusResponse(BaseModel):
    video_id: str
    status: str
    progress: float
    last_watched_at: Optional[str] = None
    watched_duration: int

    class Config:
        from_attributes = True


# =========================================================================
# Parental Control 家长控制
# =========================================================================

class ParentalControlConfig(BaseModel):
    enabled: bool = False
    daily_time_limit_minutes: int = 0
    daily_video_count_limit: int = 0
    max_single_video_duration_minutes: int = 0
    allowed_start_hour: int = -1
    allowed_start_minute: int = 0
    allowed_end_hour: int = -1
    allowed_end_minute: int = 0
    reset_hour: int = 0
    watch_completion_threshold: float = 0.8
    allow_current_video_finish: bool = True
    short_max_duration_minutes: int = 5
    medium_max_duration_minutes: int = 15
    short_video_count_limit: int = 0
    medium_video_count_limit: int = 0
    long_video_count_limit: int = 0
    block_short_video: bool = False
    block_medium_video: bool = False
    block_long_video: bool = False


class ParentalControlResponse(BaseModel):
    enabled: bool
    daily_time_limit_minutes: int
    daily_video_count_limit: int
    max_single_video_duration_minutes: int
    allowed_start_hour: int
    allowed_start_minute: int
    allowed_end_hour: int
    allowed_end_minute: int
    reset_hour: int
    watch_completion_threshold: float
    allow_current_video_finish: bool
    short_max_duration_minutes: int
    medium_max_duration_minutes: int
    short_video_count_limit: int
    medium_video_count_limit: int
    long_video_count_limit: int
    block_short_video: bool
    block_medium_video: bool
    block_long_video: bool
    updated_at: Optional[str] = None


# =========================================================================
# Watch Stats 每日观看统计
# =========================================================================

class WatchStatsUpdate(BaseModel):
    watched_time_seconds: int = 0
    watched_video_count: int = 0
    short_video_count: int = 0
    medium_video_count: int = 0
    long_video_count: int = 0


class WatchStatsResponse(BaseModel):
    date: str
    watched_time_seconds: int
    watched_video_count: int
    short_video_count: int
    medium_video_count: int
    long_video_count: int
    updated_at: Optional[str] = None
