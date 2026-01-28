package com.example.travel.companion.service;

import com.example.travel.common.exception.BusinessException;
import com.example.travel.companion.dto.CompanionDtos;
import com.example.travel.companion.entity.CompanionPost;
import com.example.travel.companion.entity.CompanionTeam;
import com.example.travel.companion.entity.TeamMember;
import com.example.travel.companion.repository.CompanionPostRepository;
import com.example.travel.companion.repository.CompanionTeamRepository;
import com.example.travel.companion.repository.TeamMemberRepository;
import com.example.travel.user.entity.User;
import com.example.travel.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CompanionService {

    private final CompanionPostRepository companionPostRepository;
    private final CompanionTeamRepository companionTeamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    public CompanionService(CompanionPostRepository companionPostRepository,
                            CompanionTeamRepository companionTeamRepository,
                            TeamMemberRepository teamMemberRepository,
                            UserRepository userRepository) {
        this.companionPostRepository = companionPostRepository;
        this.companionTeamRepository = companionTeamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return (username.contains("@")
                ? userRepository.findByEmail(username)
                : userRepository.findByPhone(username))
                .orElseThrow(() -> BusinessException.unauthorized("用户未登录"));
    }

    @Transactional
    public Long publish(CompanionDtos.PublishRequest req) {
        if (req.getEndDate().isBefore(req.getStartDate())) {
            throw BusinessException.badRequest("结束日期不能早于开始日期");
        }
        User creator = getCurrentUser();
        CompanionPost post = new CompanionPost();
        post.setCreator(creator);
        post.setRelatedPlanId(req.getRelatedPlanId());
        post.setDestination(req.getDestination());
        post.setStartDate(req.getStartDate());
        post.setEndDate(req.getEndDate());
        post.setMinPeople(req.getMinPeople());
        post.setMaxPeople(req.getMaxPeople());
        post.setBudgetMin(req.getBudgetMin());
        post.setBudgetMax(req.getBudgetMax());
        post.setExpectedMateDesc(req.getExpectedMateDesc());
        if (StringUtils.hasText(req.getVisibility())) {
            post.setVisibility(req.getVisibility());
        }
        companionPostRepository.save(post);
        return post.getId();
    }

    public List<CompanionDtos.PostSummary> search(CompanionDtos.SearchRequest req) {
        String dest = StringUtils.hasText(req.getDestination()) ? req.getDestination() : "";
        LocalDate start = req.getStartDate() != null ? req.getStartDate() : LocalDate.now().minusMonths(1);
        LocalDate end = req.getEndDate() != null ? req.getEndDate() : LocalDate.now().plusYears(1);
        List<CompanionPost> posts = companionPostRepository
                .findByDestinationContainingAndStartDateGreaterThanEqualAndEndDateLessThanEqual(dest, start, end);
        return posts.stream().map(this::toSummary).collect(Collectors.toList());
    }

    public List<CompanionDtos.PostSummary> myPosts() {
        User current = getCurrentUser();
        List<CompanionPost> posts = companionPostRepository.findByCreatorOrderByCreatedAtDesc(current);
        return posts.stream().map(this::toSummary).collect(Collectors.toList());
    }

    public CompanionDtos.PostDetail getPostDetail(Long postId) {
        CompanionPost post = companionPostRepository.findById(postId)
                .orElseThrow(() -> BusinessException.badRequest("结伴信息不存在"));
        CompanionDtos.PostDetail detail = new CompanionDtos.PostDetail();
        detail.setId(post.getId());
        detail.setDestination(post.getDestination());
        detail.setStartDate(post.getStartDate());
        detail.setEndDate(post.getEndDate());
        detail.setMinPeople(post.getMinPeople());
        detail.setMaxPeople(post.getMaxPeople());
        detail.setBudgetMin(post.getBudgetMin());
        detail.setBudgetMax(post.getBudgetMax());
        detail.setStatus(post.getStatus());
        detail.setCreatorNickname(post.getCreator() != null ? post.getCreator().getEmail() : "");
        detail.setRelatedPlanId(post.getRelatedPlanId());
        detail.setExpectedMateDesc(post.getExpectedMateDesc());
        companionTeamRepository.findFirstByPostOrderByIdAsc(post)
                .ifPresent(team -> detail.setTeamId(team.getId()));
        return detail;
    }

    public CompanionDtos.TeamDetail getTeamDetail(Long teamId) {
        CompanionTeam team = companionTeamRepository.findById(teamId)
                .orElseThrow(() -> BusinessException.badRequest("小队不存在"));
        CompanionPost post = team.getPost();
        CompanionDtos.TeamDetail detail = new CompanionDtos.TeamDetail();
        detail.setId(team.getId());
        detail.setName(team.getName());
        detail.setStatus(team.getStatus());
        if (post != null) {
            detail.setPostId(post.getId());
            detail.setDestination(post.getDestination());
            detail.setStartDate(post.getStartDate());
            detail.setEndDate(post.getEndDate());
            detail.setRelatedPlanId(post.getRelatedPlanId());
        }
        List<TeamMember> members = teamMemberRepository.findByTeam(team);
        List<CompanionDtos.TeamMemberItem> items = members.stream().map(m -> {
            CompanionDtos.TeamMemberItem item = new CompanionDtos.TeamMemberItem();
            item.setUserId(m.getUser().getId());
            item.setUserName(m.getUser().getEmail());
            item.setRole(m.getRole());
            item.setState(m.getState());
            return item;
        }).collect(Collectors.toList());
        detail.setMembers(items);
        return detail;
    }

    private CompanionDtos.PostSummary toSummary(CompanionPost post) {
        CompanionDtos.PostSummary dto = new CompanionDtos.PostSummary();
        dto.setId(post.getId());
        dto.setDestination(post.getDestination());
        dto.setStartDate(post.getStartDate());
        dto.setEndDate(post.getEndDate());
        dto.setMinPeople(post.getMinPeople());
        dto.setMaxPeople(post.getMaxPeople());
        dto.setBudgetMin(post.getBudgetMin());
        dto.setBudgetMax(post.getBudgetMax());
        dto.setStatus(post.getStatus());
        dto.setCreatorNickname(post.getCreator() != null ? post.getCreator().getEmail() : "");
        dto.setRelatedPlanId(post.getRelatedPlanId());
        return dto;
    }

    @Transactional
    public Long createTeam(Long postId) {
        User current = getCurrentUser();
        CompanionPost post = companionPostRepository.findById(postId)
                .orElseThrow(() -> BusinessException.badRequest("结伴信息不存在"));

        CompanionTeam team = new CompanionTeam();
        team.setPost(post);
        team.setName(post.getDestination() + " 小队");
        team.setStatus("forming");
        companionTeamRepository.save(team);

        TeamMember leader = new TeamMember();
        leader.setTeam(team);
        leader.setUser(current);
        leader.setRole("leader");
        leader.setState("joined");
        teamMemberRepository.save(leader);

        return team.getId();
    }

    @Transactional
    public void joinTeam(Long teamId) {
        User current = getCurrentUser();
        CompanionTeam team = companionTeamRepository.findById(teamId)
                .orElseThrow(() -> BusinessException.badRequest("小队不存在"));

        boolean exists = teamMemberRepository.findByTeamAndUser(team, current).isPresent();
        if (exists) {
            return;
        }

        TeamMember member = new TeamMember();
        member.setTeam(team);
        member.setUser(current);
        member.setRole("member");
        member.setState("joined");
        teamMemberRepository.save(member);
    }
}

