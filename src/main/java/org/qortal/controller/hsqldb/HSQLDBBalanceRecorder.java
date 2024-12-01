package org.qortal.controller.hsqldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.data.account.AccountBalanceData;
import org.qortal.repository.hsqldb.HSQLDBCacheUtils;
import org.qortal.settings.Settings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class HSQLDBBalanceRecorder extends Thread{

    private static final Logger LOGGER = LogManager.getLogger(HSQLDBBalanceRecorder.class);

    private static HSQLDBBalanceRecorder SINGLETON = null;

    private ConcurrentHashMap<Integer, List<AccountBalanceData>> balancesByHeight = new ConcurrentHashMap<>();

    private ConcurrentHashMap<String, List<AccountBalanceData>> balancesByAddress = new ConcurrentHashMap<>();

    private int priorityRequested;
    private int frequency;
    private int capacity;

    private HSQLDBBalanceRecorder( int priorityRequested, int frequency, int capacity) {

        super("Balance Recorder");

        this.priorityRequested = priorityRequested;
        this.frequency = frequency;
        this.capacity = capacity;
    }

    public static Optional<HSQLDBBalanceRecorder> getInstance() {

        if( SINGLETON == null ) {

            SINGLETON
                = new HSQLDBBalanceRecorder(
                    Settings.getInstance().getBalanceRecorderPriority(),
                    Settings.getInstance().getBalanceRecorderFrequency(),
                    Settings.getInstance().getBalanceRecorderCapacity()
            );

        }
        else if( SINGLETON == null ) {

            return Optional.empty();
        }

        return Optional.of(SINGLETON);
    }

    @Override
    public void run() {

        Thread.currentThread().setName("Balance Recorder");

        HSQLDBCacheUtils.startRecordingBalances(this.balancesByHeight, this.balancesByAddress, this.priorityRequested, this.frequency, this.capacity);
    }

    public List<AccountBalanceData> getLatestRecordings(int limit, long offset) {
        ArrayList<AccountBalanceData> data;

        Optional<Integer> lastHeight = getLastHeight();

        if(lastHeight.isPresent() ) {
            List<AccountBalanceData> latest = this.balancesByHeight.get(lastHeight.get());

            if( latest != null ) {
                data = new ArrayList<>(latest.size());
                data.addAll(
                    latest.stream()
                        .sorted(Comparator.comparingDouble(AccountBalanceData::getBalance).reversed())
                        .skip(offset)
                        .limit(limit)
                        .collect(Collectors.toList())
                );
            }
            else {
                data = new ArrayList<>(0);
            }
        }
        else {
            data = new ArrayList<>(0);
        }

        return data;
    }

    private Optional<Integer> getLastHeight() {
        return this.balancesByHeight.keySet().stream().sorted(Comparator.reverseOrder()).findFirst();
    }

    public List<Integer> getBlocksRecorded() {

        return this.balancesByHeight.keySet().stream().collect(Collectors.toList());
    }

    public List<AccountBalanceData> getAccountBalanceRecordings(String address) {
        return this.balancesByAddress.get(address);
    }

    @Override
    public String toString() {
        return "HSQLDBBalanceRecorder{" +
                "priorityRequested=" + priorityRequested +
                ", frequency=" + frequency +
                ", capacity=" + capacity +
                '}';
    }
}
