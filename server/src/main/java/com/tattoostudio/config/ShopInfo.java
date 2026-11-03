package com.tattoostudio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shop details used in emails, configurable under {@code app.shop.*}.
 *
 * @param websiteUrl where customers open the site, used for the "manage your booking" link
 */
@ConfigurationProperties("app.shop")
public record ShopInfo(String name, String address, String phone, String websiteUrl) {
}
