package com.wuxx.diagnosis.arthas;

public interface ArthasTunnelClient {

    String execute(String agentId, String command, int timeoutMillis) throws Exception;
}
