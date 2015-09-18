package com.nike.mm.repository.ws.impl

import com.google.gerrit.extensions.api.GerritApi
import com.google.gerrit.extensions.common.ChangeInfo
import com.nike.mm.dto.GerritRequestDto
import com.nike.mm.repository.ws.IGerritWsRepository
import com.urswolfer.gerrit.client.rest.GerritAuthData
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.ssl.SSLContextBuilder
import org.springframework.stereotype.Repository

@Repository
class GerritWsRepository implements IGerritWsRepository {
    HttpClientBuilderExtension builderExtension;

    Map<String, GerritApi> clients = [:]

    GerritWsRepository() {
        builderExtension = new HttpClientBuilderExtension() {
            @Override
            HttpClientBuilder extend(HttpClientBuilder httpClientBuilder, GerritAuthData authData) {
                SSLContextBuilder builder = new SSLContextBuilder();
                builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
                SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                        builder.build());
                httpClientBuilder.setSSLSocketFactory(sslsf)
                return super.extend(httpClientBuilder, authData)
            }
        }
    }

    GerritApi getClient(final GerritRequestDto dto) {
        GerritApi client = clients.get(dto.url)
        if(!client) {
            if (dto.ignoreSsl) {
                client = new GerritRestApiFactory().create(new GerritAuthData.Basic(dto.url, dto.user, dto.password), builderExtension)
            } else {
                client = new GerritRestApiFactory().create(new GerritAuthData.Basic(dto.url, dto.user, dto.password))
            }
            clients.put(dto.url, client)
        }
        return client
    }

    @Override
    List<ChangeInfo> getChanges(GerritRequestDto dto) {
        def query = "project:$dto.query.projectName"
        if (dto.query?.fromDate != null) {
            String fromDate = dto.query.fromDate.format("YYYY-MM-dd%20HH:mm:ss", TimeZone.getTimeZone("UTC"))
            query += "+after:%22$fromDate%22"
        }

        return getClient(dto).changes().query(query).withLimit(dto.query.limit).withStart(dto.query.start).get()
    }

    @Override
    ChangeInfo getChangeDetails(GerritRequestDto dto, String id) {
        return getClient(dto).changes().id(id).get()
    }
}
