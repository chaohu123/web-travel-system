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

