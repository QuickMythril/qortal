package org.qortal.controller.hsqldb;

import org.qortal.data.arbitrary.ArbitraryResourceCache;
import org.qortal.repository.RepositoryManager;
import org.qortal.repository.hsqldb.HSQLDBCacheUtils;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.settings.Settings;

public class HSQLDBDataCacheManager extends Thread{

    private ArbitraryResourceCache cache = ArbitraryResourceCache.getInstance();
    private HSQLDBRepository respository;

    public HSQLDBDataCacheManager(HSQLDBRepository respository) {
        this.respository = respository;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("HSQLDB Data Cache Manager");

        this.cache
            = HSQLDBCacheUtils.startCaching(
                Settings.getInstance().getDbCacheThreadPriority(),
                Settings.getInstance().getDbCacheFrequency(),
                this.respository
        );
    }
}
