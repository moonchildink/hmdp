package com.hmdp.utils;

public class SystemConstants {
    public static final String IMAGE_UPLOAD_DIR = "D:\\code\\Java\\hmdp\\src\\main\\resources\\nginx-1.18.0\\nginx-1.18.0\\html\\hmdp\\imgs";
    public static final String USER_NICK_NAME_PREFIX = "user_";

    public static final String SESSION_CODE_ATTRIBUTE = "code";

    public static final String REDIS_CODE_PREFIX  = "code_phone_login: ";
    public static final String REDIS_LOGIN_PREFIX = "login:token:";

    public static  final long LOGIN_TOKEN_TIME = 30L;

    public static final String SECKILL_STOCK = "seckill:stock:";

    public static  final String LIKED_USER_ZSET= "blog:liked:";

    public static final String SESSION_PHONE_ATTRIBUTE = "phone";

    public static final long REDIS_NULL_TTL = 3L;

    public static final String REDIS_CACHE = "cache:shop:";

    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;
    public static final String FOLLOWED_USERS = "follows:";
    public static final String FEED_KEY = "feeds:";
}
