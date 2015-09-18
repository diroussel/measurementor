package com.nike.mm.repository.ws

import com.google.gerrit.extensions.common.ChangeInfo
import com.nike.mm.dto.GerritRequestDto

public interface IGerritWsRepository {

    List<ChangeInfo> getChanges(GerritRequestDto dto)

    ChangeInfo getChangeDetails(GerritRequestDto dto, String id)

}
