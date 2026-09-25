package com.example.NotesNest.utils.constants;

public final class PrefKeys {

    private PrefKeys() {
        // Prevent instantiation
    }

    // PREMIUM RELATED
    public static final String IS_LOGGED_IN = "is_login";
    public static final String THEME = "theme";
    public static final String SYSTEM_THEME = "system_theme";
    public static final String PLAN_TYPE = "plan_type";
    public static final String PURCHASE_DATE = "purchase_date";
    public static final String PREMIUM_EXPIRY_DATE = "premium_expiry_date";
    public static final String PREMIUM_PLAN_TYPE = "premium_plan";
    public static final String IS_PREMIUM = "is_premium";

    // User Related
    public static final String USER_ID = "user_id";
    public static final String USER_IMAGE = "user_image";
    public static final String USER_EMAIL = "user_email";
    public static final String USER_NAME = "user_name";
    public static final String PURCHASE_TOKEN = "purchase_token";
    public static final String ORDER_ID = "order_id";
    public static final String IS_ONBOARDING_COMPLETED = "completed";

    // Backup related
    public static final String BACKUP_MODE = "backup_mode";
    public static final String BACKUP_ACCOUNT_EMAIL = "backup_account_email";
    public static final String BACKUP_USER_NAME = "backup_user_name";
    public static final String BACKUP_USER_IMAGE = "backup_user_image";
    public static final String IS_SIGNED_IN = "is_signed_in";
    public static final String AUTO_BACKUP_ENABLED = "auto_backup_enabled";
    public static final String INCLUDE_ATTACHMENTS = "include_attachments";
    public static final String LAST_BACKUP_TIME = "last_backup_time";
    public static final String DRIVE_BACKUP_PASSWORD = "drive_backup_password";

    // Edit Note Draft
    public static final String KEY_DRAFT_CONTENT = "draft_content";
    public static final String KEY_DRAFT_TITLE = "draft_title";
    public static final String KEY_DRAFT_COLOR = "draft_color";

    // Layout Settings
    public static final String KEY_NOTES_LAYOUT = "notes_layout";
}
