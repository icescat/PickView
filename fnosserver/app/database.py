import sqlite3
import uuid
import os
from datetime import datetime
from typing import List, Optional
from pathlib import Path

from models import (
    VideoCreate, Video, CategoryCreate, Category,
    SeriesCreate, Series, SeriesUpdate,
    WatchStatusUpdate, WatchStatusResponse,
)


DEFAULT_CATEGORIES = [
    ("语文", 0),
    ("数学", 1),
    ("外语", 2),
    ("科学", 3),
    ("历史", 4),
    ("政治", 5),
    ("生物", 6),
    ("地理", 7),
    ("其他", 8),
]

# 旧版默认分类，用于迁移检测
_LEGACY_CATEGORIES = {"科技", "纪录片"}


class Database:
    def __init__(self, db_path: str = "data/videos.db"):
        self.db_path = db_path
        self._ensure_data_dir()
        self._init_db()

    def _ensure_data_dir(self):
        data_dir = Path(self.db_path).parent
        data_dir.mkdir(parents=True, exist_ok=True)
        os.chmod(str(data_dir), 0o777)

    def _init_db(self):
        try:
            with sqlite3.connect(self.db_path) as conn:
                cursor = conn.cursor()
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS videos (
                        id TEXT PRIMARY KEY,
                        bvid TEXT UNIQUE NOT NULL,
                        title TEXT NOT NULL,
                        up_name TEXT NOT NULL,
                        duration INTEGER NOT NULL,
                        category TEXT NOT NULL DEFAULT '',
                        cover TEXT NOT NULL DEFAULT '',
                        added_at TEXT NOT NULL,
                        added_by TEXT NOT NULL
                    )
                """)
                # 兼容旧库：补充 cover 字段
                self._safe_add_column(cursor, "videos", "cover", "TEXT NOT NULL DEFAULT ''")
                # 新增：系列关联字段
                self._safe_add_column(cursor, "videos", "series_id", "TEXT NOT NULL DEFAULT ''")
                self._safe_add_column(cursor, "videos", "episode_index", "INTEGER NOT NULL DEFAULT 0")

                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS categories (
                        id TEXT PRIMARY KEY,
                        name TEXT UNIQUE NOT NULL,
                        display_order INTEGER NOT NULL DEFAULT 0
                    )
                """)

                # 系列表
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS series (
                        id TEXT PRIMARY KEY,
                        title TEXT NOT NULL,
                        cover TEXT NOT NULL DEFAULT '',
                        description TEXT NOT NULL DEFAULT '',
                        display_order INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL
                    )
                """)

                # 观看记录表
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS watch_history (
                        id TEXT PRIMARY KEY,
                        video_id TEXT NOT NULL UNIQUE,
                        status TEXT NOT NULL DEFAULT 'unwatched',
                        progress REAL NOT NULL DEFAULT 0,
                        last_watched_at TEXT,
                        watched_duration INTEGER NOT NULL DEFAULT 0,
                        updated_at TEXT NOT NULL
                    )
                """)

                # 家长控制配置表（单条记录）
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS parental_control (
                        id INTEGER PRIMARY KEY DEFAULT 1,
                        enabled INTEGER NOT NULL DEFAULT 0,
                        daily_time_limit_minutes INTEGER NOT NULL DEFAULT 0,
                        daily_video_count_limit INTEGER NOT NULL DEFAULT 0,
                        max_single_video_duration_minutes INTEGER NOT NULL DEFAULT 0,
                        allowed_start_hour INTEGER NOT NULL DEFAULT -1,
                        allowed_start_minute INTEGER NOT NULL DEFAULT 0,
                        allowed_end_hour INTEGER NOT NULL DEFAULT -1,
                        allowed_end_minute INTEGER NOT NULL DEFAULT 0,
                        reset_hour INTEGER NOT NULL DEFAULT 0,
                        watch_completion_threshold REAL NOT NULL DEFAULT 0.8,
                        allow_current_video_finish INTEGER NOT NULL DEFAULT 1,
                        short_max_duration_minutes INTEGER NOT NULL DEFAULT 5,
                        medium_max_duration_minutes INTEGER NOT NULL DEFAULT 15,
                        short_video_count_limit INTEGER NOT NULL DEFAULT 0,
                        medium_video_count_limit INTEGER NOT NULL DEFAULT 0,
                        long_video_count_limit INTEGER NOT NULL DEFAULT 0,
                        block_short_video INTEGER NOT NULL DEFAULT 0,
                        block_medium_video INTEGER NOT NULL DEFAULT 0,
                        block_long_video INTEGER NOT NULL DEFAULT 0,
                        updated_at TEXT
                    )
                """)
                # 兼容旧库：补齐新增列（若已存在则跳过）
                self._safe_add_column(cursor, "parental_control", "block_short_video", "INTEGER NOT NULL DEFAULT 0")
                self._safe_add_column(cursor, "parental_control", "block_medium_video", "INTEGER NOT NULL DEFAULT 0")
                self._safe_add_column(cursor, "parental_control", "block_long_video", "INTEGER NOT NULL DEFAULT 0")
                # 插入默认配置（如果不存在）
                cursor.execute("INSERT OR IGNORE INTO parental_control (id) VALUES (1)")

                # 每日观看统计表（含短/中/长视频计数）
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS watch_stats (
                        date TEXT PRIMARY KEY,
                        watched_time_seconds INTEGER NOT NULL DEFAULT 0,
                        watched_video_count INTEGER NOT NULL DEFAULT 0,
                        short_video_count INTEGER NOT NULL DEFAULT 0,
                        medium_video_count INTEGER NOT NULL DEFAULT 0,
                        long_video_count INTEGER NOT NULL DEFAULT 0,
                        updated_at TEXT
                    )
                """)

                self._init_default_categories(cursor)
                conn.commit()
        except Exception as e:
            print(f"数据库初始化失败: {e}")
            raise

    @staticmethod
    def _safe_add_column(cursor, table: str, column: str, definition: str):
        """安全添加列，已存在则跳过"""
        try:
            cursor.execute(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")
        except sqlite3.OperationalError:
            pass

    def _init_default_categories(self, cursor):
        # 旧版分类迁移：检测到旧分类则替换为新版
        cursor.execute("SELECT name FROM categories")
        existing_names = {row[0] for row in cursor.fetchall()}
        legacy_found = existing_names & _LEGACY_CATEGORIES
        if legacy_found and "语文" not in existing_names:
            # 将使用旧分类的视频改为"其他"
            for name in legacy_found:
                cursor.execute(
                    "UPDATE videos SET category = '其他' WHERE category = ?",
                    (name,)
                )
                cursor.execute("DELETE FROM categories WHERE name = ?", (name,))
            # 添加新默认分类
            for name, order in DEFAULT_CATEGORIES:
                if name not in existing_names:
                    cat_id = str(uuid.uuid4())[:8]
                    cursor.execute(
                        "INSERT INTO categories (id, name, display_order) VALUES (?, ?, ?)",
                        (cat_id, name, order)
                    )
            return

        cursor.execute("SELECT COUNT(*) FROM categories")
        count = cursor.fetchone()[0]
        if count == 0:
            for name, order in DEFAULT_CATEGORIES:
                cat_id = str(uuid.uuid4())[:8]
                cursor.execute(
                    "INSERT INTO categories (id, name, display_order) VALUES (?, ?, ?)",
                    (cat_id, name, order)
                )

    # =========================================================================
    # Videos
    # =========================================================================

    def create_video(self, video: VideoCreate) -> Video:
        video_id = str(uuid.uuid4())[:8]
        added_at = datetime.now().isoformat()

        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO videos (id, bvid, title, up_name, duration, category, cover, added_at, added_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                video_id, video.bvid, video.title, video.up_name,
                video.duration, video.category, video.cover,
                added_at, video.added_by
            ))
            conn.commit()

        return Video(
            id=video_id, bvid=video.bvid, title=video.title,
            up_name=video.up_name, duration=video.duration,
            category=video.category, cover=video.cover,
            added_at=added_at, added_by=video.added_by
        )

    def get_video(self, video_id: str) -> Optional[Video]:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM videos WHERE id = ?", (video_id,))
            row = cursor.fetchone()
            if row:
                return self._row_to_video(row)
            return None

    def get_video_by_bvid(self, bvid: str) -> Optional[Video]:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM videos WHERE bvid = ?", (bvid,))
            row = cursor.fetchone()
            if row:
                return self._row_to_video(row)
            return None

    def get_all_videos(self, category: Optional[str] = None) -> List[Video]:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            if category:
                cursor.execute(
                    "SELECT * FROM videos WHERE category = ? ORDER BY added_at DESC",
                    (category,)
                )
            else:
                cursor.execute("SELECT * FROM videos ORDER BY added_at DESC")
            rows = cursor.fetchall()
            return [self._row_to_video(row) for row in rows]

    def update_video_category(self, video_id: str, category: str) -> bool:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute(
                "UPDATE videos SET category = ? WHERE id = ?",
                (category, video_id)
            )
            conn.commit()
            return cursor.rowcount > 0

    def delete_video(self, video_id: str) -> bool:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM videos WHERE id = ?", (video_id,))
            conn.commit()
            return cursor.rowcount > 0

    def batch_delete_videos(self, video_ids: List[str]) -> int:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            placeholders = ",".join("?" for _ in video_ids)
            cursor.execute(
                f"DELETE FROM videos WHERE id IN ({placeholders})",
                video_ids
            )
            conn.commit()
            return cursor.rowcount

    def batch_update_video_category(self, video_ids: List[str], category: str) -> int:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            placeholders = ",".join("?" for _ in video_ids)
            cursor.execute(
                f"UPDATE videos SET category = ? WHERE id IN ({placeholders})",
                [category] + video_ids
            )
            conn.commit()
            return cursor.rowcount

    def _row_to_video(self, row: tuple) -> Video:
        # 字段顺序: id, bvid, title, up_name, duration, category, cover, added_at, added_by, series_id, episode_index
        # 兼容旧库：新字段可能不存在
        series_id = row[9] if len(row) > 9 else ""
        episode_index = row[10] if len(row) > 10 else 0
        return Video(
            id=row[0], bvid=row[1], title=row[2], up_name=row[3],
            duration=row[4], category=row[5], cover=row[6],
            added_at=row[7], added_by=row[8],
            series_id=series_id, episode_index=episode_index
        )

    # =========================================================================
    # Categories
    # =========================================================================

    def get_all_categories(self) -> List[Category]:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM categories ORDER BY display_order")
            rows = cursor.fetchall()
            return [Category(id=row[0], name=row[1], display_order=row[2]) for row in rows]

    def create_category(self, cat: CategoryCreate) -> Category:
        cat_id = str(uuid.uuid4())[:8]
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute(
                "INSERT INTO categories (id, name, display_order) VALUES (?, ?, ?)",
                (cat_id, cat.name, cat.display_order)
            )
            conn.commit()
        return Category(id=cat_id, name=cat.name, display_order=cat.display_order)

    def delete_category(self, cat_id: str) -> bool:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM categories WHERE id = ?", (cat_id,))
            conn.commit()
            return cursor.rowcount > 0

    def update_category(self, cat_id: str, name: Optional[str] = None, display_order: Optional[int] = None) -> bool:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            if name is not None and display_order is not None:
                cursor.execute(
                    "UPDATE categories SET name = ?, display_order = ? WHERE id = ?",
                    (name, display_order, cat_id)
                )
            elif name is not None:
                cursor.execute(
                    "UPDATE categories SET name = ? WHERE id = ?",
                    (name, cat_id)
                )
            elif display_order is not None:
                cursor.execute(
                    "UPDATE categories SET display_order = ? WHERE id = ?",
                    (display_order, cat_id)
                )
            else:
                return False
            conn.commit()
            return cursor.rowcount > 0

    def get_category_by_name(self, name: str) -> Optional[Category]:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM categories WHERE name = ?", (name,))
            row = cursor.fetchone()
            if row:
                return Category(id=row[0], name=row[1], display_order=row[2])
            return None

    # =========================================================================
    # Series 系列
    # =========================================================================

    def create_series(self, series: SeriesCreate) -> Series:
        series_id = str(uuid.uuid4())[:8]
        created_at = datetime.now().isoformat()
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO series (id, title, cover, description, display_order, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
            """, (
                series_id, series.title, series.cover,
                series.description, series.display_order, created_at
            ))
            conn.commit()
        return Series(
            id=series_id, title=series.title, cover=series.cover,
            description=series.description, display_order=series.display_order,
            created_at=created_at
        )

    def get_series(self, series_id: str) -> Optional[Series]:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM series WHERE id = ?", (series_id,))
            row = cursor.fetchone()
            if row:
                return self._row_to_series(row)
            return None

    def get_all_series(self) -> List[tuple]:
        """返回 (Series, video_count) 列表"""
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM series ORDER BY display_order, created_at")
            rows = cursor.fetchall()
            result = []
            for row in rows:
                series = self._row_to_series(row)
                cursor.execute("SELECT COUNT(*) FROM videos WHERE series_id = ?", (series.id,))
                count = cursor.fetchone()[0]
                result.append((series, count))
            return result

    def update_series(self, series_id: str, update: SeriesUpdate) -> bool:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            fields = []
            values = []
            if update.title is not None:
                fields.append("title = ?")
                values.append(update.title)
            if update.cover is not None:
                fields.append("cover = ?")
                values.append(update.cover)
            if update.description is not None:
                fields.append("description = ?")
                values.append(update.description)
            if update.display_order is not None:
                fields.append("display_order = ?")
                values.append(update.display_order)
            if not fields:
                return False
            values.append(series_id)
            cursor.execute(f"UPDATE series SET {', '.join(fields)} WHERE id = ?", values)
            conn.commit()
            return cursor.rowcount > 0

    def delete_series(self, series_id: str) -> bool:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            # 清除视频中该系列关联
            cursor.execute("UPDATE videos SET series_id = '', episode_index = 0 WHERE series_id = ?", (series_id,))
            cursor.execute("DELETE FROM series WHERE id = ?", (series_id,))
            conn.commit()
            return cursor.rowcount > 0

    def add_videos_to_series(self, series_id: str, video_ids: List[str], episode_index: Optional[int] = None) -> int:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            # 获取当前最大 episode_index
            if episode_index is None:
                cursor.execute("SELECT COALESCE(MAX(episode_index), -1) FROM videos WHERE series_id = ?", (series_id,))
                next_index = cursor.fetchone()[0] + 1
            else:
                next_index = episode_index
            count = 0
            for vid in video_ids:
                cursor.execute(
                    "UPDATE videos SET series_id = ?, episode_index = ? WHERE id = ?",
                    (series_id, next_index, vid)
                )
                if cursor.rowcount > 0:
                    count += 1
                next_index += 1
            conn.commit()
            return count

    def remove_video_from_series(self, series_id: str, video_id: str) -> bool:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute(
                "UPDATE videos SET series_id = '', episode_index = 0 WHERE id = ? AND series_id = ?",
                (video_id, series_id)
            )
            conn.commit()
            return cursor.rowcount > 0

    def get_series_videos(self, series_id: str) -> List[Video]:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT * FROM videos WHERE series_id = ? ORDER BY episode_index",
                (series_id,)
            )
            rows = cursor.fetchall()
            return [self._row_to_video(row) for row in rows]

    def update_series_video_order(self, orders: List[dict]) -> int:
        """批量更新系列内视频顺序"""
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            count = 0
            for item in orders:
                cursor.execute(
                    "UPDATE videos SET episode_index = ? WHERE id = ?",
                    (item["episode_index"], item["video_id"])
                )
                if cursor.rowcount > 0:
                    count += 1
            conn.commit()
            return count

    @staticmethod
    def _row_to_series(row: tuple) -> Series:
        return Series(
            id=row[0], title=row[1], cover=row[2],
            description=row[3], display_order=row[4], created_at=row[5]
        )

    # =========================================================================
    # Watch History 观看记录
    # =========================================================================

    def get_watch_status(self, video_id: str) -> Optional[WatchStatusResponse]:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM watch_history WHERE video_id = ?", (video_id,))
            row = cursor.fetchone()
            if row:
                return WatchStatusResponse(
                    video_id=row[1], status=row[2], progress=row[3],
                    last_watched_at=row[4], watched_duration=row[5]
                )
            return None

    def get_watch_status_batch(self, video_ids: List[str]) -> dict:
        """批量获取观看状态，返回 {video_id: WatchStatusResponse}"""
        if not video_ids:
            return {}
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            placeholders = ",".join("?" for _ in video_ids)
            cursor.execute(
                f"SELECT * FROM watch_history WHERE video_id IN ({placeholders})",
                video_ids
            )
            rows = cursor.fetchall()
            result = {}
            for row in rows:
                result[row[1]] = WatchStatusResponse(
                    video_id=row[1], status=row[2], progress=row[3],
                    last_watched_at=row[4], watched_duration=row[5]
                )
            return result

    def upsert_watch_status(self, video_id: str, update: WatchStatusUpdate) -> WatchStatusResponse:
        now = datetime.now().isoformat()
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT id FROM watch_history WHERE video_id = ?", (video_id,))
            existing = cursor.fetchone()
            if existing:
                cursor.execute("""
                    UPDATE watch_history
                    SET status = ?, progress = ?, last_watched_at = ?,
                        watched_duration = ?, updated_at = ?
                    WHERE video_id = ?
                """, (
                    update.status, update.progress, now,
                    update.watched_duration, now, video_id
                ))
            else:
                record_id = str(uuid.uuid4())[:8]
                cursor.execute("""
                    INSERT INTO watch_history (id, video_id, status, progress, last_watched_at, watched_duration, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """, (
                    record_id, video_id, update.status, update.progress,
                    now, update.watched_duration, now
                ))
            conn.commit()
        return WatchStatusResponse(
            video_id=video_id, status=update.status, progress=update.progress,
            last_watched_at=now, watched_duration=update.watched_duration
        )

    # =========================================================================
    # Parental Control 家长控制
    # =========================================================================

    def get_parental_control(self) -> dict:
        """获取家长控制配置（单条记录，id=1）
        使用显式列名查询，避免 ALTER TABLE 后列顺序错位导致索引失配。
        """
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("""
                SELECT enabled, daily_time_limit_minutes, daily_video_count_limit,
                       max_single_video_duration_minutes, allowed_start_hour, allowed_start_minute,
                       allowed_end_hour, allowed_end_minute, reset_hour, watch_completion_threshold,
                       allow_current_video_finish, short_max_duration_minutes, medium_max_duration_minutes,
                       short_video_count_limit, medium_video_count_limit, long_video_count_limit,
                       block_short_video, block_medium_video, block_long_video, updated_at
                FROM parental_control WHERE id = 1
            """)
            row = cursor.fetchone()
            if row:
                return {
                    "enabled": bool(row[0]),
                    "daily_time_limit_minutes": row[1],
                    "daily_video_count_limit": row[2],
                    "max_single_video_duration_minutes": row[3],
                    "allowed_start_hour": row[4],
                    "allowed_start_minute": row[5],
                    "allowed_end_hour": row[6],
                    "allowed_end_minute": row[7],
                    "reset_hour": row[8],
                    "watch_completion_threshold": row[9],
                    "allow_current_video_finish": bool(row[10]),
                    "short_max_duration_minutes": row[11],
                    "medium_max_duration_minutes": row[12],
                    "short_video_count_limit": row[13],
                    "medium_video_count_limit": row[14],
                    "long_video_count_limit": row[15],
                    "block_short_video": bool(row[16]),
                    "block_medium_video": bool(row[17]),
                    "block_long_video": bool(row[18]),
                    "updated_at": row[19],
                }
            return None

    def save_parental_control(self, config: dict) -> dict:
        """保存家长控制配置"""
        now = datetime.now().isoformat()
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("""
                UPDATE parental_control SET
                    enabled = ?,
                    daily_time_limit_minutes = ?,
                    daily_video_count_limit = ?,
                    max_single_video_duration_minutes = ?,
                    allowed_start_hour = ?,
                    allowed_start_minute = ?,
                    allowed_end_hour = ?,
                    allowed_end_minute = ?,
                    reset_hour = ?,
                    watch_completion_threshold = ?,
                    allow_current_video_finish = ?,
                    short_max_duration_minutes = ?,
                    medium_max_duration_minutes = ?,
                    short_video_count_limit = ?,
                    medium_video_count_limit = ?,
                    long_video_count_limit = ?,
                    block_short_video = ?,
                    block_medium_video = ?,
                    block_long_video = ?,
                    updated_at = ?
                WHERE id = 1
            """, (
                int(config.get("enabled", False)),
                config.get("daily_time_limit_minutes", 0),
                config.get("daily_video_count_limit", 0),
                config.get("max_single_video_duration_minutes", 0),
                config.get("allowed_start_hour", -1),
                config.get("allowed_start_minute", 0),
                config.get("allowed_end_hour", -1),
                config.get("allowed_end_minute", 0),
                config.get("reset_hour", 0),
                config.get("watch_completion_threshold", 0.8),
                int(config.get("allow_current_video_finish", True)),
                config.get("short_max_duration_minutes", 5),
                config.get("medium_max_duration_minutes", 15),
                config.get("short_video_count_limit", 0),
                config.get("medium_video_count_limit", 0),
                config.get("long_video_count_limit", 0),
                int(config.get("block_short_video", False)),
                int(config.get("block_medium_video", False)),
                int(config.get("block_long_video", False)),
                now,
            ))
            conn.commit()
        return self.get_parental_control()

    # =========================================================================
    # Watch Stats 每日观看统计
    # =========================================================================

    def get_watch_stats(self, date: str) -> dict:
        """获取某日观看统计"""
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("""
                SELECT date, watched_time_seconds, watched_video_count,
                       short_video_count, medium_video_count, long_video_count,
                       updated_at
                FROM watch_stats WHERE date = ?
            """, (date,))
            row = cursor.fetchone()
            if row:
                return {
                    "date": row[0],
                    "watched_time_seconds": row[1],
                    "watched_video_count": row[2],
                    "short_video_count": row[3],
                    "medium_video_count": row[4],
                    "long_video_count": row[5],
                    "updated_at": row[6],
                }
            return {
                "date": date,
                "watched_time_seconds": 0,
                "watched_video_count": 0,
                "short_video_count": 0,
                "medium_video_count": 0,
                "long_video_count": 0,
                "updated_at": None,
            }

    def update_watch_stats(self, date: str, update: dict) -> dict:
        """更新某日观看统计（增量更新）"""
        now = datetime.now().isoformat()
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT date FROM watch_stats WHERE date = ?", (date,))
            existing = cursor.fetchone()
            if existing:
                cursor.execute("""
                    UPDATE watch_stats SET
                        watched_time_seconds = watched_time_seconds + ?,
                        watched_video_count = watched_video_count + ?,
                        short_video_count = short_video_count + ?,
                        medium_video_count = medium_video_count + ?,
                        long_video_count = long_video_count + ?,
                        updated_at = ?
                    WHERE date = ?
                """, (
                    update.get("watched_time_seconds", 0),
                    update.get("watched_video_count", 0),
                    update.get("short_video_count", 0),
                    update.get("medium_video_count", 0),
                    update.get("long_video_count", 0),
                    now, date,
                ))
            else:
                cursor.execute("""
                    INSERT INTO watch_stats
                    (date, watched_time_seconds, watched_video_count,
                     short_video_count, medium_video_count, long_video_count, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """, (
                    date,
                    update.get("watched_time_seconds", 0),
                    update.get("watched_video_count", 0),
                    update.get("short_video_count", 0),
                    update.get("medium_video_count", 0),
                    update.get("long_video_count", 0),
                    now,
                ))
            conn.commit()
        return self.get_watch_stats(date)


db = Database()
