package com.example.demo.repository;

import com.example.demo.domain.Problem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProblemRepository extends MongoRepository<Problem, String> {
    // solvedUsers 필드가 아키텍처 V3.0에서 분리 및 삭제되었으므로
    // 여기서 사용하던 옛날 쿼리 메서드들은 제거합니다.
}
