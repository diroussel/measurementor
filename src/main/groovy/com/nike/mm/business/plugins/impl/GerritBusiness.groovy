package com.nike.mm.business.plugins.impl

import com.google.common.collect.Lists
import com.google.gerrit.extensions.common.ChangeInfo
import com.nike.mm.business.plugins.IGerritBusiness
import com.nike.mm.dto.GerritRequestDto
import com.nike.mm.dto.JobRunResponseDto
import com.nike.mm.entity.internal.JobHistory
import com.nike.mm.entity.plugins.GerritChange
import com.nike.mm.repository.es.plugins.IGerritChangeRepository
import com.nike.mm.repository.ws.IGerritWsRepository
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

import java.util.concurrent.TimeUnit

@Slf4j
@Service
class GerritBusiness extends AbstractBusiness implements IGerritBusiness {
    /**
     * Error message when proxy port is missing
     */
    static final String MISSING_PROXY_PORT = "Missing proxy port"

    /**
     * Error message when proxy port is not a positive integer
     */
    static final String INVALID_PROXY_PORT = "Proxy must be a positive integer"

    /**
     * Error message when credentials are missing
     */
    static final String MISSING_CREDENTIALS = "Missing credentials"

    static final String MISSING_PROJECT_NAME = "Missing project names"

    @Autowired
    IGerritWsRepository gerritWsRepository;

    @Autowired
    IGerritChangeRepository gerritChangeRepository;

    @Override
    String type() {
        return "Gerrit"
    }

    @Override
    String validateConfig(Object config) {
        final List<String> validationErrors = Lists.newArrayList()
        if (!config.url) {
            validationErrors.add(MISSING_URL)
        }
        if (!config.user) {
            validationErrors.add(MISSING_CREDENTIALS)
        }
        if (!config.password) {
            validationErrors.add(MISSING_CREDENTIALS)
        }
        if (!config.projectNames) {
            validationErrors.add(MISSING_PROJECT_NAME)
        }
        if (config.proxyUrl) {
            if (!config.proxyPort) {
                validationErrors.add(MISSING_PROXY_PORT)
            } else {
                if (!config.proxyPort.toString().isInteger() || (0 >= config.proxyPort.toString().toInteger())) {
                    validationErrors.add(INVALID_PROXY_PORT)
                }
            }
        }
        return buildValidationErrorString(validationErrors)
    }

    @Override
    JobRunResponseDto updateDataWithResponse(final Date defaultFromDate, final Object configInfo) {
        int recordsCount = 0

        configInfo.projectNames.each { def final projectName ->
            Date fromDate = defaultFromDate

            final def pageable = new PageRequest(0, 1, new Sort(Sort.Direction.DESC, "lastUpdated"))
            final Page<GerritChange> gerritPage = this.gerritChangeRepository.findByProjectName(projectName, pageable)
            if (gerritPage.content.size()) {
                fromDate = gerritPage.content[0].lastUpdated
            }
            final GerritRequestDto dto = new GerritRequestDto(url: configInfo.url, user: configInfo.user, password: configInfo.password,
                    ignoreSsl: configInfo.ignoreSsl, query: [projectName: projectName, start: 0, limit: 100, fromDate: fromDate], proxyDto: this.getProxyDto(configInfo))
            recordsCount += this.updateProjectData(projectName, fromDate, dto)
        }

        final
        def jobResponseDto = new JobRunResponseDto(type: type(), status: JobHistory.Status.success, recordsCount: recordsCount)
        return jobResponseDto
    }

    int updateProjectData(final String projectName, final Date fromDate, final GerritRequestDto dto) {
        int updatedRecordsCount = 0

        List<ChangeInfo> changes = this.gerritWsRepository.getChanges(dto)
        if (changes) {
            if (changes.size() > 0) {
                changes.each { def i ->
                    def ChangeInfo changeInfo = this.gerritWsRepository.getChangeDetails(dto, i.id) // this gets more details
                    def ReviewLogDto reviewLogDto = new ReviewLogDto(changeInfo)
                    saveGerritData(changeInfo, reviewLogDto)
                    updatedRecordsCount++
                }
                log.debug("Retrieved $updatedRecordsCount records for project $projectName")
            }
            if (changes.last()._moreChanges) {
                dto.query.start += dto.query.limit
                log.debug("NEXT PAGE starting at $dto.query.start")
                updatedRecordsCount += this.updateProjectData(projectName, fromDate, dto)
            }
        } else {
            log.debug("Skipping project $projectName as no updated records where found")
        }
        return updatedRecordsCount
    }

    def saveGerritData(final ChangeInfo changeInfo, final ReviewLogDto reviewLog) {
        def gerritData = this.gerritChangeRepository.findByGerritId(changeInfo._number)
        if (gerritData) {
            gerritData.lastUpdated = new Date(changeInfo.updated.time)
            gerritData.numberOfPatchSets = changeInfo.revisions.size()
            gerritData.totalReviewTimeMinutes = reviewLog.totalReviewTimeMinutes
        } else {
            def jiraKey = ""
            def jiraKeyMatcher = (changeInfo.subject =~ "([A-Z]+-[0-9]+).*(:)?.*")
            if (jiraKeyMatcher.matches()) {
                jiraKey = jiraKeyMatcher[0][1]
            }
            gerritData = new GerritChange(
                    gerritId: changeInfo._number,
                    created: new Date(changeInfo.created.time),
                    lastUpdated: new Date(changeInfo.updated.time),
                    projectName: changeInfo.project,
                    branch: changeInfo.branch,
                    jiraKey: jiraKey,
                    numberOfPatchSets: changeInfo.revisions.size(),
                    totalReviewTimeMinutes: reviewLog.totalReviewTimeMinutes

            )
        }
        this.gerritChangeRepository.save(gerritData)
    }


    class ReviewLogDto {
        def reviewTimePerAuthor = [:]
        def reviewStartTime = [:]
        def reviewEndTime = [:]
        def totalReviewTime = 0
        def totalReviewTimeMinutes = 0

        ReviewLogDto(final def changeDetails) {
            changeDetails.messages.each { def m ->
                def authorName = m.author?.username
                if (!authorName) return

                if (m.message.contains("REVIEW_STARTED")) {
                    reviewStartTime[authorName] = m.date.getTime()
                } else if (m.message.contains("Code-Review") && reviewStartTime[authorName] > 0) {
                    reviewEndTime[authorName] = m.date.getTime()
                }
                if (reviewStartTime[authorName] != null && reviewStartTime[authorName] > 0
                        && reviewEndTime[authorName] != null && reviewEndTime[authorName] > 0) {
                    def reviewTime = reviewEndTime[authorName] - reviewStartTime[authorName]
                    reviewTimePerAuthor[authorName] = reviewTime
                    totalReviewTime += reviewTime
                    reviewStartTime[authorName] = 0
                    reviewEndTime[authorName] = 0
                }
            }
            if (totalReviewTime > 0) {
                totalReviewTimeMinutes = TimeUnit.MILLISECONDS.toMinutes(totalReviewTime);
            }
            log.debug("Change-Number: $changeDetails._number, totalReviewTimeMinutes: $totalReviewTimeMinutes")
        }
    }

}
