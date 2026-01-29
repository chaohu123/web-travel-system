package com.example.travel.user.dto;

import lombok.Data;

@Data
public class UserDtos {

    @Data
    public static class MeDetail {
        private Long id;
        private String email;
        private String phone;
        private String nickname;
        private String avatar;
        private String city;
        private String gender;
        private Integer age;
        private String intro;
        private String slogan;
        private Integer reputationScore;
        private Integer reputationLevel;
    }

    /**
     * 公开的用户主页信息（对外展示）
     */
    @Data
    public static class UserPublicProfile {
        private Long id;
        private String nickname;
        private String avatar;
        private String city;
        private String gender;
        private Integer age;
        private String intro;
        private String slogan;
        private String coverImage;

        private Integer reputationScore;
        private Integer reputationLevel;

        private Long followersCount;
        private Long followingCount;
        private Boolean isFollowed;

        private Preference preferences;
        private Stats stats;

        @Data
        public static class Preference {
            private String[] travelStyles;
            private String[] interests;
            private String budgetRange;
            private String[] transportPreferences;
        }

        @Data
        public static class Stats {
            private Long completedRoutes;
            private Long notesCount;
            private Long companionSuccessCount;
            private Long likedCount;
            private Long favoritedCount;
        }
    }

    @Data
    public static class UpdateProfileRequest {
        private String nickname;
        private String avatar;
        private String gender;
        private Integer age;
        private String city;
        private String intro;
        private String slogan;
    }
}

