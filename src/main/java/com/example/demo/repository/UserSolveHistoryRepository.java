package com.example.demo.repository;

import com.example.demo.domain.UserSolveHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface UserSolveHistoryRepository extends MongoRepository<UserSolveHistory, String> {

    // 1. 단일 문제 덮어쓰기용 (가장 최근 기록 1개)
    Optional<UserSolveHistory> findFirstByUserIdAndProblemIdOrderBySolvedAtDesc(String userId, String problemId);

    // 2. [전체 보기] 유저의 모든 풀이 기록 최신순
    List<UserSolveHistory> findByUserIdOrderBySolvedAtDesc(String userId);

    // 3. [과목별 보기] 특정 과목(예: 물류관리론)의 풀이 기록만 최신순
    List<UserSolveHistory> findByUserIdAndSubjectTypeOrderBySolvedAtDesc(String userId, String subjectType);

    // 4. [단원별 보기] 특정 단원(예: 물류총론)의 풀이 기록만 최신순
    List<UserSolveHistory> findByUserIdAndSubCategoryOrderBySolvedAtDesc(String userId, String subCategory);
}