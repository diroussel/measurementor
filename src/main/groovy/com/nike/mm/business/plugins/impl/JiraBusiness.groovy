package com.nike.mm.business.plugins.impl

import com.google.common.collect.Lists
import com.nike.mm.business.plugins.IJiraBusiness
import com.nike.mm.dto.HttpRequestDto
import com.nike.mm.dto.JobRunResponseDto
import com.nike.mm.dto.ProxyDto
import com.nike.mm.entity.internal.JobHistory
import com.nike.mm.entity.plugins.Jira
import com.nike.mm.entity.plugins.JiraHistory
import com.nike.mm.repository.es.plugins.IJiraEsRepository
import com.nike.mm.repository.es.plugins.IJiraHistoryEsRepository
import com.nike.mm.repository.ws.IJiraWsRepository
import com.nike.mm.service.IUtilitiesService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

import java.util.concurrent.TimeUnit

@Slf4j
@Service
class JiraBusiness extends AbstractBusiness implements IJiraBusiness {

//    /**
//     * Error message when proxy url is missing
//     */
//    static final String MISSING_PROXY_URL = "Missing proxy url"
//
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

    @Autowired
    IJiraWsRepository jiraWsRepository

    @Autowired
    IUtilitiesService utilitiesService

    @Autowired
    IJiraHistoryEsRepository jiraHistoryEsRepository

    @Autowired
    IJiraEsRepository jiraEsRepository

    @Override
    String type() {
        return "Jira";
    }

    @Override
    String validateConfig(final Object config) {
        final List<String> validationErrors = Lists.newArrayList()
        if (!config.url) {
            validationErrors.add(MISSING_URL)
        }
        if (!config.credentials) {
            validationErrors.add(MISSING_CREDENTIALS)
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
        this.getProjects(configInfo).each { def final projectName ->

            Date fromDate = defaultFromDate
            final def pageable = new PageRequest(0, 1, new Sort(Sort.Direction.DESC, "created"))
            final Page<Jira> jiraPage = this.jiraEsRepository.findByJiraProject(projectName, pageable)
            if (jiraPage.content.size()) {
                fromDate = jiraPage.content[0].created
            }

            def final path = "/rest/api/2/search"
            def final jiraQuery = "project=$projectName AND updatedDate>" + fromDate.getTime() + " order by updatedDate " +
                    "asc"
            def final query = [jql: jiraQuery, expand: "changelog", startAt: 0, maxResults: 100, fields: "*all"]
            final HttpRequestDto dto = [url: configInfo.url, path: path, query: query, credentials: configInfo.credentials,
                                  proxyDto: this.getProxyDto(configInfo)] as HttpRequestDto

            recordsCount += this.updateProjectData(projectName, dto, configInfo.projectConfigs[projectName])
        }

        final def jobResponseDto = new JobRunResponseDto(type: type(), status: JobHistory.Status.success, recordsCount:
                recordsCount)
        return jobResponseDto
    }

    List<String> getProjects(final Object configInfo) {
        final def path = "/rest/api/2/project"

        HttpRequestDto dto = [url: configInfo.url, path: path, query: [start: 0, limit: 300], credentials: configInfo
                .credentials, proxyDto: this.getProxyDto(configInfo)] as HttpRequestDto
        return this.jiraWsRepository.getProjectsList(dto)
    }

    int updateProjectData(final String projectName, final HttpRequestDto dto, final Object projectConfig) {
        boolean keepGoing = false
        int updatedRecordsCount = 0

        final def json = this.jiraWsRepository.getDataForProject(dto)

        if (json && json.issues && json.issues?.size() > 0) {
            keepGoing = true
            json.issues.each { def i ->
                final OtherItemsDto otherItemsDto = new OtherItemsDto(i)
                ChangelogHistoryItemDto changelogHistoryItemDto = new ChangelogHistoryItemDto()
                if (i.changelog) {
                    changelogHistoryItemDto = new ChangelogHistoryItemDto(i, projectConfig.taskStatusMap, otherItemsDto.issueType)
                }
                final LeadTimeDevTimeDto leadTimeDevTimeDto = new LeadTimeDevTimeDto(i, changelogHistoryItemDto
                        .movedToDevList.min())
                this.saveJiraData(projectName, i, changelogHistoryItemDto, leadTimeDevTimeDto, otherItemsDto)
                updatedRecordsCount++
            }
            log.debug("Retrieved $updatedRecordsCount records for Project $projectName ")
        } else {
            log.debug("Skipping project $projectName as no updated records where found")
        }

        if (keepGoing) {
            dto.query.startAt += dto.query.maxResults
            log.debug("NEXT PAGE starting at $dto.query.startAt")
            updatedRecordsCount += this.updateProjectData(projectName, dto, projectConfig)
        }
        return updatedRecordsCount
    }

    def saveJiraData(final String projectName,
                     final def i,
                     final ChangelogHistoryItemDto changelogHistoryItemDto,
                     final LeadTimeDevTimeDto leadTimeDevTimeDto,
                     final OtherItemsDto otherItemsDto) {
        //TODO - need a way to figure out estimates based on input
        final def estimateHealth = this.utilitiesService.estimateHealth(otherItemsDto.storyPoints, leadTimeDevTimeDto
                .devTime, 13, 9, [1, 2, 3, 5, 8, 13])
        final def recidivism = (changelogHistoryItemDto.moveForward) ?
                (changelogHistoryItemDto.moveBackward / (changelogHistoryItemDto.moveBackward + changelogHistoryItemDto.moveForward) * 50) : null;

        def jiraData = this.jiraEsRepository.findByKey(i.key)
        if (jiraData) {
            jiraData.createdBy = this.utilitiesService.cleanEmail(i.fields.creator?.emailAddress)
            jiraData.issueType = otherItemsDto.issueType
            jiraData.movedForward = changelogHistoryItemDto.moveForward
            jiraData.movedBackward = changelogHistoryItemDto.moveBackward
            jiraData.recidivism = recidivism
            jiraData.fixedVersions = i.fields.fixVersions*.name
            jiraData.affectsVersions = i.fields.versions*.name
            jiraData.storyPoints = otherItemsDto.storyPoints
            jiraData.finished = this.utilitiesService.cleanJiraDate(i.fields.resolutiondate)
            jiraData.assignees = changelogHistoryItemDto.assignees
            jiraData.tags = i.fields.labels
            jiraData.dataType = "PTS"
            jiraData.leadTime = leadTimeDevTimeDto.leadTime
            jiraData.devTime = leadTimeDevTimeDto.devTime >= 1 ? leadTimeDevTimeDto.devTime : otherItemsDto.estimateHours
            jiraData.commentCount = i.fields.comment?.total
            jiraData.jiraProject = projectName
            jiraData.rawEstimateHealth = estimateHealth.raw
            jiraData.estimateHealth = estimateHealth.result
            jiraData.components = otherItemsDto.components
            jiraData.product = otherItemsDto.product
            jiraData.priority = i.fields.priority.name
            jiraData.resolution = i.fields.resolution?.name
            jiraData.estimate = otherItemsDto.estimateHours
            jiraData.timesReopened = changelogHistoryItemDto.timesReopened
        } else {
            jiraData = new Jira(
                    key: i.key,
                    created: this.utilitiesService.cleanJiraDate(i.fields.created),
                    createdBy: this.utilitiesService.cleanEmail(i.fields.creator?.emailAddress),
                    issueType: otherItemsDto.issueType,
                    movedForward: changelogHistoryItemDto.moveForward,
                    movedBackward: changelogHistoryItemDto.moveBackward,
                    recidivism: recidivism,
                    fixedVersions: i.fields.fixVersions*.name,
                    affectsVersions: i.fields.versions*.name,
                    storyPoints: otherItemsDto.storyPoints,
                    finished: this.utilitiesService.cleanJiraDate(i.fields.resolutiondate),
                    assignees: changelogHistoryItemDto.assignees,
                    tags: i.fields.labels,
                    dataType: "PTS",
                    leadTime: leadTimeDevTimeDto.leadTime,
                    devTime: leadTimeDevTimeDto.devTime >= 1 ? leadTimeDevTimeDto.devTime : otherItemsDto.estimateHours,
                    commentCount: i.fields.comment?.total,
                    jiraProject: projectName,
                    estimateHealth: estimateHealth.result,
                    rawEstimateHealth: estimateHealth.raw,
                    components: otherItemsDto.components,
                    product: otherItemsDto.product,
                    priority: i.fields.priority.name,
                    resolution: i.fields.resolution?.name,
                    estimate: otherItemsDto.estimateHours,
                    timesReopened: changelogHistoryItemDto.timesReopened)
        }
        this.jiraEsRepository.save(jiraData)
    }

    class OtherItemsDto {
        String issueType = ""
        List components = []
        String product = ""
        Integer storyPoints = 0
        Float estimateHours = 0

        OtherItemsDto(final def i) {
            this.issueType = this.getIssueType(i)
            this.components = this.getComponentsList(i)
            this.product = this.getProductString(i)
            this.storyPoints = this.getStoryPoints(i)
            this.estimateHours = this.getEstimateHours(i)
        }

        String getIssueType(final def i) {
            def issueType = ""
            if (i.fields.issuetype?.name) {
                issueType = i.fields.issuetype.name.replace(" ", "_")
            }
            return issueType
        }

        List getComponentsList(final def i) {
            final List components = []
            for (def c : i.fields.components) {
                components.add(c.name)
            }
            return components
        }

        String getProductString(final def i) {
            String product = ""
            if (i.fields.customfield_12040) {
                product = i.fields.customfield_12040.value
                if (i.fields.customfield_12040.child) {
                    product += " " + i.fields.customfield_12040.child.value
                }
            }
            return product
        }

        Integer getStoryPoints(final def i) {
            def storyPoints = 0
            if (i.fields.customfield_10013) {
                storyPoints = i.fields.customfield_10013.toInteger()
            }
            return storyPoints;
        }

        Float getEstimateHours(final def i) {
            def Float estimateHours = 0
            if (i.fields.timeestimate) {
                estimateHours = i.fields.timeestimate.toInteger() / 3600
            }
            return estimateHours;
        }
    }

    class ChangelogHistoryItemDto {
        def moveForward = 0
        def moveBackward = 0
        def timesReopened = 0
        def assignees = []
        def movedToDevList = []

        /**
         * Default constructor in the case that we have no change log information
         */
        ChangelogHistoryItemDto() {}

        /**
         * In the event that we have changelog infomation.
         * @param i - The json array from the result list.
         */
        ChangelogHistoryItemDto(final def i, final def taskStatusMap, final def issueType) {

            // add default create entry
            def openEntries = JiraBusiness.this.jiraHistoryEsRepository.findByKeyAndNewValue(i.key, "Open")
            if (openEntries.isEmpty()) {
                def firstHistory = [
                        dataType   : "PTS",
                        timestamp  : JiraBusiness.this.utilitiesService.cleanJiraDate(i.fields.created),
                        changeField: "status",
                        newValue   : "Open",
                        changedBy  : JiraBusiness.this.utilitiesService.cleanEmail(i.fields.creator?.emailAddress),
                        key        : i.key,
                        issueType  : issueType,
                        fixedVersions: i.fields.fixVersions*.name,
                        affectsVersions: i.fields.versions*.name
                ] as JiraHistory
                JiraBusiness.this.jiraHistoryEsRepository.save(firstHistory)
            }

            def boolean reopenedAutomatically = false;
            def int stateBeforeAutoReopen = 0;
            for (def h : i.changelog.histories) {
                for (def t : h.items) {
                    //NOTE the following conditionals flatten history into stuff we can work with easier
                    if (t.field == "status") {
                        //NOTE get the progression for churn
                        // ignore reopen by opding user
                        if (taskStatusMap[t.toString] == 1) {
                            if (h.author.name == "opdingbuild") {
                                reopenedAutomatically = true
                                stateBeforeAutoReopen = taskStatusMap[t.fromString]
                                continue;
                            } else {
                                reopenedAutomatically = false
                                this.timesReopened++
                            }
                        }
                        if (!reopenedAutomatically || taskStatusMap[t.fromString] == stateBeforeAutoReopen) {
                            // ignore equal values
                            if (taskStatusMap[t.fromString] > taskStatusMap[t.toString]) {
                                // handle actual movement distance by status distance
                                this.moveBackward += (taskStatusMap[t.fromString] - taskStatusMap[t.toString]).toInteger()
                            } else if (taskStatusMap[t.fromString] < taskStatusMap[t.toString]) {
                                // handle actual movement distance by status distance
                                this.moveForward += (taskStatusMap[t.toString] - taskStatusMap[t.fromString]).toInteger()
                                this.movedToDevList.add(JiraBusiness.this.utilitiesService.cleanJiraDate(h.created))
                            }
                        } else {
                            continue;
                        }
                    } else if (t.field == "assignee") {
                        //NOTE get everyone that worked on this issue, or at least was assigned to it
                        if (t.toString) {
                            this.assignees.add(JiraBusiness.this.utilitiesService.makeNonTokenFriendly(t.toString))
                        }
                    }

                    def history = JiraBusiness.this.jiraHistoryEsRepository.findByKeyAndSourceId(i.key, h.id)
                    //no updates for history entries
                    if (history == null) {
                        String emailAddress = null
                        if (h.author?.emailAddress) {
                            emailAddress = h.author.emailAddress
                        } else if (h.author?.name) {
                            emailAddress = h.author.name
                        }
                        emailAddress = JiraBusiness.this.utilitiesService.cleanEmail(emailAddress)
                        history = new JiraHistory(
                                dataType   : "PTS",
                                sourceId   : h.id,
                                timestamp  : JiraBusiness.this.utilitiesService.cleanJiraDate(h.created),
                                changeField: t.field,
                                newValue   : t.toString,
                                changedBy  : emailAddress,
                                key        : i.key,
                                issueType  : issueType,
                                fixedVersions: i.fields.fixVersions*.name,
                                affectsVersions: i.fields.versions*.name)
                        JiraBusiness.this.jiraHistoryEsRepository.save(history)
                    }
                }
            }
        }
    }

    class LeadTimeDevTimeDto {
        def leadTime = 0
        def Float devTime = 0

        LeadTimeDevTimeDto(final def i, final def movedToDev) {
            log.debug("Fields: " + i.fields.created)
            final def createdDate = JiraBusiness.this.utilitiesService.cleanJiraDate(i.fields.created)
            final def fin = JiraBusiness.this.utilitiesService.cleanJiraDate(i.fields.resolutiondate)
            if (createdDate) {
                def endLeadTime = new Date()
                if (fin) {
                    endLeadTime = fin
                }
                final long duration = endLeadTime.getTime() - createdDate.getTime()
                leadTime = TimeUnit.MILLISECONDS.toDays(duration)
                if (leadTime == 0) {
                    leadTime = 1
                }
            }

            if (movedToDev) {
                def endLeadTime = new Date()
                if (fin) {
                    endLeadTime = fin
                }
                final long duration = endLeadTime.getTime() - movedToDev.getTime()
                // to get partial hours, toHours only returns full hours
                devTime = TimeUnit.MILLISECONDS.toMinutes(duration) / 60.0
            }
        }
    }
}
