package org.apache.doris.spark.util;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;

public class DorisHttpClients {

	static RequestConfig DEFAULT_REQUEST_CONFIG = RequestConfig.custom()
			.setConnectTimeout(30000)  // 连接超时时间(毫秒)
			.setConnectionRequestTimeout(30000) //设置从连接池获取连接的超时时间(毫秒)
			.setSocketTimeout(15 * 60 * 1000) // 设置socket超时时间(毫秒)，即两次数据包之间的最大间隔时间
			.build();

	public static CloseableHttpClient createDefault() {
		return HttpClients.custom()
				.build();
	}

	public static HttpClientBuilder custom() {
		HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
		httpClientBuilder.setDefaultRequestConfig(DEFAULT_REQUEST_CONFIG);
		return httpClientBuilder;
	}
}
