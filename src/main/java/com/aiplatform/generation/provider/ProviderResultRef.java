package com.aiplatform.generation.provider;

/** 上游结果清单中的单个位置；引用只指向渠道声明的下载地址，不携带用户或路径信息。 */
public record ProviderResultRef(int position, String ref, String digest) {
}
