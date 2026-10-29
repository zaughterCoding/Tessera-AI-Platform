package com.aiplatform.community.service;

import com.aiplatform.community.dto.CreationDTO;
import com.aiplatform.community.dto.CreationForm;
import com.aiplatform.community.dto.CreationPage;
import com.aiplatform.community.dto.CreationUpdateForm;
import com.aiplatform.community.dto.LikeResult;

public interface ICreationService {
  CreationDTO create(long userId, CreationForm form);

  CreationDTO update(long userId, long creationId, CreationUpdateForm form);

  CreationDTO setVisibility(long userId, long creationId, boolean published);

  void delete(long userId, long creationId);

  CreationDTO publicDetail(Long viewerId, long creationId);

  CreationDTO ownedDetail(long userId, long creationId);

  CreationPage publicPage(Long viewerId, int limit, int offset);

  CreationPage hotPage(Long viewerId, int limit, int offset);

  CreationPage applicationPage(Long viewerId, long applicationId, int limit, int offset);

  CreationPage authorPage(Long viewerId, long authorId, int limit, int offset);

  CreationPage mine(long userId, int limit, int offset);

  /** 明确目标状态；重复请求只得到同一状态与同一计数。 */
  LikeResult setLiked(long userId, long creationId, boolean liked);
}
