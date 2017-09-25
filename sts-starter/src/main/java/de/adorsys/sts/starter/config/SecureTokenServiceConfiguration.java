package de.adorsys.sts.starter.config;

import de.adorsys.sts.admin.EnableAdmin;
import de.adorsys.sts.pop.EnablePOP;
import de.adorsys.sts.resourceserver.ResourceServerManager;
import de.adorsys.sts.resourceserver.ResourceServerProcessor;
import de.adorsys.sts.serverinfo.EnableServerInfo;
import de.adorsys.sts.token.passwordgrant.EnablePasswordGrant;
import de.adorsys.sts.token.tokenexchange.EnableTokenExchange;
import de.adorsys.sts.worksheetloader.DataSheetLoader;
import de.adorsys.sts.worksheetloader.LoginLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnablePOP
@EnableTokenExchange
@EnablePasswordGrant
@EnableAdmin
@EnableServerInfo
public class SecureTokenServiceConfiguration {

    @Bean
    public ResourceServerProcessor resourceServerProcessor() {
        return new ResourceServerProcessor();
    }

    @Bean
    public ResourceServerManager resourceServerManager() {
        return new ResourceServerManager();
    }

    @Bean
    public DataSheetLoader dataSheetLoader() {
        return new DataSheetLoader();
    }

    @Bean
    public LoginLoader loginLoader() {
        return new LoginLoader();
    }
}
