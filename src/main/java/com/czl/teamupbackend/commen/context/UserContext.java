package com.czl.teamupbackend.commen.context;

import com.czl.teamupbackend.model.entity.User;

/**
 * User context manager for current thread.
 */
public class UserContext {

    private static final ThreadLocal<LoginUserInfo> USER_HOLDER = new ThreadLocal<>();

    /**
     * Lightweight user info stored in thread local.
     */
    public static class LoginUserInfo {
        private final Long id;
        private final String username;

        public LoginUserInfo(Long id, String username) {
            this.id = id;
            this.username = username;
        }

        public Long getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }
    }

    /**
     * Set current logged-in user from entity.
     */
    public static void setCurrentUser(User user) {
        if (user == null) {
            USER_HOLDER.remove();
            return;
        }
        USER_HOLDER.set(new LoginUserInfo(user.getId(), user.getUsername()));
    }

    /**
     * Set current logged-in user from minimal fields.
     */
    public static void setCurrentUser(Long userId, String username) {
        USER_HOLDER.set(new LoginUserInfo(userId, username));
    }

    /**
     * Get current logged-in user info.
     */
    public static LoginUserInfo getCurrentUser() {
        return USER_HOLDER.get();
    }

    /**
     * Get current logged-in user id.
     */
    public static Long getCurrentUserId() {
        LoginUserInfo user = USER_HOLDER.get();
        return user != null ? user.getId() : null;
    }

    /**
     * Get current logged-in username.
     */
    public static String getCurrentUsername() {
        LoginUserInfo user = USER_HOLDER.get();
        return user != null ? user.getUsername() : null;
    }

    /**
     * Remove current user info.
     */
    public static void removeCurrentUser() {
        USER_HOLDER.remove();
    }

    /**
     * Is user logged in.
     */
    public static boolean isLoggedIn() {
        return USER_HOLDER.get() != null;
    }
}
