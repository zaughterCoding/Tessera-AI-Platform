package com.aiplatform.community.service;

import com.aiplatform.community.dto.SignInResult;

public interface ISignInService {
  SignInResult signIn(long userId);

  SignInResult status(long userId);

  int consecutiveDays(long userId);
}
