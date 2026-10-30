package com.example.demo.repository;

import com.example.demo.domain.Problem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProblemRepository extends MongoRepository<Problem, String> {

    // [Server A] 문제 서빙용: 해당 단원들에 속한 문제를 전부 가져옴
    List<Problem> findByDomainIdIn(List<String> domainIds);

    // [Server A] 워터마크 방어용: 풀스캔 없이 인덱스를 타서 문제 갯수만 초고속 카운트
    long countByDomainIdIn(List<String> domainIds);
}
