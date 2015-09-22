package com.nike.mm.repository.es.plugins

import com.nike.mm.entity.plugins.GerritChange
import com.nike.mm.entity.plugins.Jira
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository

interface IGerritChangeRepository extends ElasticsearchRepository<GerritChange, Long> {

    Page<GerritChange> findByProjectName(String projectName, Pageable pageable);

}