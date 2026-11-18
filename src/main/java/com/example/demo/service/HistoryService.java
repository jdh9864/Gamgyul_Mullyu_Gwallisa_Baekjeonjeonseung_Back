package com.example.demo.service;

import com.example.demo.domain.Problem;
import com.example.demo.domain.SubjectDomain;
import com.example.demo.domain.UserSolveHistory;
import com.example.demo.dto.HistoryDto;
import com.example.demo.repository.ProblemRepository;
import com.example.demo.repository.SubjectDomainRepository;
import com.example.demo.repository.UserSolveHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Profile("serverB")
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final UserSolveHistoryRepository historyRepository;
    private final ProblemRepository problemRepository;
    private final SubjectDomainRepository subjectDomainRepository;

    /**
     * 유저별 풀이/오답 기록 목록 조회 (문제 원문 $in 단건 일괄 조인)
     */
    public List<HistoryDto.Response> getHistories(
            String userId,
            boolean onlyWrong,
            String subjectType,
            String subCategory) {

        List<UserSolveHistory> rawHistories;
        if (subCategory != null && !subCategory.isBlank()) {
            rawHistories = historyRepository.findByUserIdAndSubCategoryOrderBySolvedAtDesc(userId, subCategory);
        } else if (subjectType != null && !subjectType.isBlank()) {
            rawHistories = historyRepository.findByUserIdAndSubjectTypeOrderBySolvedAtDesc(userId, subjectType);
        } else {
            rawHistories = historyRepository.findByUserIdOrderBySolvedAtDesc(userId);
        }

        // 1. 동일 문제 중복 풀이 시 가장 최신 풀이(solvedAt DESC 첫 항목)만 유지
        Map<String, UserSolveHistory> latestByProblem = new LinkedHashMap<>();
        for (UserSolveHistory h : rawHistories) {
            if (h.getProblemId() != null) {
                latestByProblem.putIfAbsent(h.getProblemId(), h);
            }
        }

        // 2. 최신 풀이 기준으로 오답 전용 필터 적용 (이전에 틀렸다가 최근 맞힌 문제 제외)
        List<UserSolveHistory> filtered = latestByProblem.values().stream()
                .filter(h -> !onlyWrong || !h.isCorrect())
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Problem 원문 단 1회 배치 조회
        Map<String, Problem> problemMap = fetchProblemMap(filtered);

        return filtered.stream()
                .map(h -> toResponse(h, problemMap.get(h.getProblemId())))
                .collect(Collectors.toList());
    }

    /**
     * 단건 오답노트 체감 난이도 및 복습 메모 수정 (미존재 시 신규 Upsert)
     */
    public HistoryDto.Response updateHistory(String userId, HistoryDto.UpdateRequest req) {
        if (req == null || req.getProblemId() == null || req.getProblemId().isBlank()) {
            throw new IllegalArgumentException("problemId는 필수입니다.");
        }

        UserSolveHistory history = historyRepository
                .findFirstByUserIdAndProblemIdOrderBySolvedAtDesc(userId, req.getProblemId())
                .orElseGet(() -> createNewHistory(userId, req));

        applyUpdates(history, req);
        UserSolveHistory saved = historyRepository.save(history);

        Problem problem = problemRepository.findById(saved.getProblemId()).orElse(null);
        return toResponse(saved, problem);
    }

    /**
     * 다건 오답노트 일괄 저장 (단일 조회 + saveAll 배치 쓰기)
     */
    public List<HistoryDto.Response> bulkUpdateHistories(String userId, HistoryDto.BulkUpdateRequest req) {
        if (req == null || req.getUpdates() == null || req.getUpdates().isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, UserSolveHistory> latestMap = new HashMap<>();
        for (UserSolveHistory h : historyRepository.findByUserIdOrderBySolvedAtDesc(userId)) {
            if (h.getProblemId() != null) {
                latestMap.putIfAbsent(h.getProblemId(), h);
            }
        }

        List<UserSolveHistory> toSave = new ArrayList<>();
        for (HistoryDto.UpdateRequest item : req.getUpdates()) {
            if (item == null || item.getProblemId() == null || item.getProblemId().isBlank()) {
                continue;
            }
            UserSolveHistory target = latestMap.computeIfAbsent(
                    item.getProblemId(),
                    pid -> createNewHistory(userId, item)
            );
            applyUpdates(target, item);
            toSave.add(target);
        }

        if (toSave.isEmpty()) {
            return Collections.emptyList();
        }

        List<UserSolveHistory> savedList = historyRepository.saveAll(toSave);
        Map<String, Problem> problemMap = fetchProblemMap(savedList);

        return savedList.stream()
                .map(h -> toResponse(h, problemMap.get(h.getProblemId())))
                .collect(Collectors.toList());
    }

    private UserSolveHistory createNewHistory(String userId, HistoryDto.UpdateRequest req) {
        UserSolveHistory h = new UserSolveHistory();
        h.setUserId(userId);
        h.setProblemId(req.getProblemId());
        h.setCorrect(false);
        h.setSolvedAt(LocalDateTime.now());

        String subjectType = req.getSubjectType();
        String subCategory = req.getSubCategory();

        if ((subjectType == null || subjectType.isBlank()) || (subCategory == null || subCategory.isBlank())) {
            Problem problem = problemRepository.findById(req.getProblemId()).orElse(null);
            if (problem != null && problem.getDomainId() != null) {
                SubjectDomain domain = subjectDomainRepository.findById(problem.getDomainId()).orElse(null);
                if (domain != null) {
                    if (subjectType == null || subjectType.isBlank()) subjectType = domain.getSubjectType();
                    if (subCategory == null || subCategory.isBlank()) subCategory = domain.getSubCategory();
                }
            }
        }

        h.setSubjectType(subjectType != null && !subjectType.isBlank() ? subjectType : "기타");
        h.setSubCategory(subCategory != null && !subCategory.isBlank() ? subCategory : "기타");
        return h;
    }

    private void applyUpdates(UserSolveHistory target, HistoryDto.UpdateRequest req) {
        if (req.getUserDifficulty() != null && !req.getUserDifficulty().isBlank()) {
            target.setUserDifficulty(req.getUserDifficulty());
        }
        if (req.getReviewNotes() != null) {
            target.setReviewNotes(req.getReviewNotes());
        }
        if ((target.getSubjectType() == null || target.getSubjectType().isBlank())
                && req.getSubjectType() != null && !req.getSubjectType().isBlank()) {
            target.setSubjectType(req.getSubjectType());
        }
        if ((target.getSubCategory() == null || target.getSubCategory().isBlank())
                && req.getSubCategory() != null && !req.getSubCategory().isBlank()) {
            target.setSubCategory(req.getSubCategory());
        }
    }

    private Map<String, Problem> fetchProblemMap(List<UserSolveHistory> histories) {
        Set<String> problemIds = histories.stream()
                .map(UserSolveHistory::getProblemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (problemIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Problem> map = new HashMap<>();
        for (Problem p : problemRepository.findAllById(problemIds)) {
            if (p.getId() != null) {
                map.putIfAbsent(p.getId(), p);
            }
        }
        return map;
    }

    private HistoryDto.Response toResponse(UserSolveHistory h, Problem p) {
        HistoryDto.Response res = new HistoryDto.Response();
        res.setHistoryId(h.getId());
        res.setProblemId(h.getProblemId());
        res.setSubjectType(h.getSubjectType());
        res.setSubCategory(h.getSubCategory());
        res.setCorrect(h.isCorrect());
        res.setUserDifficulty(h.getUserDifficulty());
        res.setReviewNotes(h.getReviewNotes());
        res.setSolvedAt(h.getSolvedAt());

        if (p != null) {
            res.setContent(p.getContent());
            res.setChoices(p.getChoices());
            res.setCorrectAnswer(p.getAnswer());
            res.setExplanation(p.getExplanation());
        }
        return res;
    }
}
