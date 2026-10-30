package com.example.demo.repository;

import com.example.demo.domain.SubjectDomain;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface SubjectDomainRepository extends MongoRepository<SubjectDomain, String> {

    // 다중 단원(subCategories 리스트)에 해당하는 도메인 정보를 한 번의 I/O로 싹 다 긁어옴
    List<SubjectDomain> findBySubjectTypeAndSubCategoryIn(String subjectType, List<String> subCategories);
}
