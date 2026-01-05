package org.qortal.test.at;

import org.ciyam.at.MachineState;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.asset.Asset;
import org.qortal.block.BlockChain;
import org.qortal.data.at.ATData;
import org.qortal.data.at.ATStateData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.DeployAtTransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.AtUtils;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.transaction.DeployAtTransaction;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class AtRepositoryTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@Test
	public void testGetATStateAtHeightWithData() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);

			Integer testHeight = 8;
			ATStateData atStateData = repository.getATRepository().getATStateAtHeight(atAddress, testHeight);

			assertEquals(testHeight, atStateData.getHeight());
			assertNotNull(atStateData.getStateData());
		}
	}

	@Test
	public void testGetATStateAtHeightWithoutData() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);

			int maxHeight = 8;
			Integer testHeight = maxHeight - 2;

			// Trim AT state data
			repository.getATRepository().rebuildLatestAtStates(maxHeight);
			repository.getATRepository().trimAtStates(2, maxHeight, 1000);

			ATStateData atStateData = repository.getATRepository().getATStateAtHeight(atAddress, testHeight);

			assertEquals(testHeight, atStateData.getHeight());
			assertNull(atStateData.getStateData());
		}
	}

	@Test
	public void testGetLatestATStateWithData() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);
			int blockchainHeight = repository.getBlockRepository().getBlockchainHeight();

			Integer testHeight = blockchainHeight;
			ATStateData atStateData = repository.getATRepository().getLatestATState(atAddress);

			assertEquals(testHeight, atStateData.getHeight());
			assertNotNull(atStateData.getStateData());
		}
	}

	@Test
	public void testGetLatestATStatePostTrimming() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);
			int blockchainHeight = repository.getBlockRepository().getBlockchainHeight();

			int maxHeight = blockchainHeight + 100; // more than latest block height
			Integer testHeight = blockchainHeight;

			// Trim AT state data
			repository.getATRepository().rebuildLatestAtStates(maxHeight);
			// COMMIT to check latest AT states persist / TEMPORARY table interaction
			repository.saveChanges();

			repository.getATRepository().trimAtStates(2, maxHeight, 1000);

			ATStateData atStateData = repository.getATRepository().getLatestATState(atAddress);

			assertEquals(testHeight, atStateData.getHeight());
			// We should always have the latest AT state data available
			assertNotNull(atStateData.getStateData());
		}
	}

	@Test
	public void testOrphanTrimmedATStates() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);

			int blockchainHeight = repository.getBlockRepository().getBlockchainHeight();
			int maxTrimHeight = blockchainHeight - 4;
			Integer testHeight = maxTrimHeight + 1;

			// Trim AT state data (using a max height of maxTrimHeight + 1, so it is beyond the trimmed range)
			repository.getATRepository().rebuildLatestAtStates(maxTrimHeight + 1);
			repository.saveChanges();
			repository.getATRepository().trimAtStates(2, maxTrimHeight, 1000);

			// Orphan 3 blocks
			// This leaves one more untrimmed block, so the latest AT state should be available
			BlockUtils.orphanBlocks(repository, 3);

			ATStateData atStateData = repository.getATRepository().getLatestATState(atAddress);
			assertEquals(testHeight, atStateData.getHeight());

			// We should always have the latest AT state data available
			assertNotNull(atStateData.getStateData());

			// Orphan 1 more block
			Exception exception = null;
			try {
				BlockUtils.orphanBlocks(repository, 1);
			} catch (DataException e) {
				exception = e;
			}

			// Ensure that a DataException is thrown because there is no more AT states data available
			assertNotNull(exception);
			assertEquals(DataException.class, exception.getClass());
			assertEquals(String.format("Can't find previous AT state data for %s", atAddress), exception.getMessage());

			// FUTURE: we may be able to retain unique AT states when trimming, to avoid this exception
			// and allow orphaning back through blocks with trimmed AT states.
		}
	}

	@Test
	public void testGetMatchingFinalATStatesWithoutDataValue() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);
			int blockchainHeight = repository.getBlockRepository().getBlockchainHeight();

			Integer testHeight = blockchainHeight;

			ATData atData = repository.getATRepository().fromATAddress(atAddress);

			byte[] codeHash = atData.getCodeHash();
			Boolean isFinished = Boolean.FALSE;
			Integer dataByteOffset = null;
			Long expectedValue = null;
			Integer minimumFinalHeight = null;
			Integer limit = null;
			Integer offset = null;
			Boolean reverse = null;

			List<ATStateData> atStates = repository.getATRepository().getMatchingFinalATStates(
					codeHash,
					null,
					null,
					isFinished,
					dataByteOffset,
					expectedValue,
					minimumFinalHeight,
					limit, offset, reverse);

			assertEquals(false, atStates.isEmpty());
			assertEquals(1, atStates.size());

			ATStateData atStateData = atStates.get(0);
			assertEquals(testHeight, atStateData.getHeight());
			assertNotNull(atStateData.getStateData());
		}
	}

	@Test
	public void testGetMatchingFinalATStatesWithDataValue() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);
			int blockchainHeight = repository.getBlockRepository().getBlockchainHeight();

			Integer testHeight = blockchainHeight;

			ATData atData = repository.getATRepository().fromATAddress(atAddress);

			byte[] codeHash = atData.getCodeHash();
			Boolean isFinished = Boolean.FALSE;
			Integer dataByteOffset = MachineState.HEADER_LENGTH + 0;
			Long expectedValue = 0L;
			Integer minimumFinalHeight = null;
			Integer limit = null;
			Integer offset = null;
			Boolean reverse = null;

			List<ATStateData> atStates = repository.getATRepository().getMatchingFinalATStates(
					codeHash,
					null,
					null,
					isFinished,
					dataByteOffset,
					expectedValue,
					minimumFinalHeight,
					limit, offset, reverse);

			assertEquals(false, atStates.isEmpty());
			assertEquals(1, atStates.size());

			ATStateData atStateData = atStates.get(0);
			assertEquals(testHeight, atStateData.getHeight());
			assertNotNull(atStateData.getStateData());
		}
	}

	@Test
	public void testGetBlockATStatesAtHeightWithData() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);

			Integer testHeight = 8;
			List<ATStateData> atStates = repository.getATRepository().getBlockATStatesAtHeight(testHeight);

			assertEquals(false, atStates.isEmpty());
			assertEquals(1, atStates.size());

			ATStateData atStateData = atStates.get(0);
			assertEquals(testHeight, atStateData.getHeight());
			// getBlockATStatesAtHeight never returns actual AT state data anyway
			assertNull(atStateData.getStateData());
		}
	}

	@Test
	public void testGetBlockATStatesAtHeightWithoutData() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);

			int maxHeight = 8;
			Integer testHeight = maxHeight - 2;

			// Trim AT state data
			repository.getATRepository().rebuildLatestAtStates(maxHeight);
			repository.getATRepository().trimAtStates(2, maxHeight, 1000);

			List<ATStateData> atStates = repository.getATRepository().getBlockATStatesAtHeight(testHeight);

			assertEquals(false, atStates.isEmpty());
			assertEquals(1, atStates.size());

			ATStateData atStateData = atStates.get(0);
			assertEquals(testHeight, atStateData.getHeight());
			// getBlockATStatesAtHeight never returns actual AT state data anyway
			assertNull(atStateData.getStateData());
		}
	}

	@Test
	public void testSaveATStateWithData() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);
			int blockchainHeight = repository.getBlockRepository().getBlockchainHeight();

			Integer testHeight = blockchainHeight - 2;
			ATStateData atStateData = repository.getATRepository().getATStateAtHeight(atAddress, testHeight);

			assertEquals(testHeight, atStateData.getHeight());
			assertNotNull(atStateData.getStateData());

			repository.getATRepository().save(atStateData);

			atStateData = repository.getATRepository().getATStateAtHeight(atAddress, testHeight);

			assertEquals(testHeight, atStateData.getHeight());
			assertNotNull(atStateData.getStateData());
		}
	}

	@Test
	public void testSaveATStateWithoutData() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);
			int blockchainHeight = repository.getBlockRepository().getBlockchainHeight();

			Integer testHeight = blockchainHeight - 2;
			ATStateData atStateData = repository.getATRepository().getATStateAtHeight(atAddress, testHeight);

			assertEquals(testHeight, atStateData.getHeight());
			assertNotNull(atStateData.getStateData());

			// Clear data
			ATStateData newAtStateData = new ATStateData(atStateData.getATAddress(),
					atStateData.getHeight(),
					/*StateData*/ null,
					atStateData.getStateHash(),
					atStateData.getFees(),
					atStateData.isInitial(),
					atStateData.getSleepUntilMessageTimestamp());
			repository.getATRepository().save(newAtStateData);

			atStateData = repository.getATRepository().getATStateAtHeight(atAddress, testHeight);

			assertEquals(testHeight, atStateData.getHeight());
			assertNull(atStateData.getStateData());
		}
	}

	@Test
	public void testDeterministicAtOrderingAfterTrigger() throws DataException, IllegalAccessException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			@SuppressWarnings("unchecked")
			Map<String, Long> featureTriggers = (Map<String, Long>) FieldUtils.readField(BlockChain.getInstance(), "featureTriggers", true);
			String triggerKey = BlockChain.FeatureTrigger.deterministicAtOrderingHeight.name();
			Long originalTrigger = featureTriggers.get(triggerKey);
			featureTriggers.put(triggerKey, 0L);

			try {
				PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

				long fundingAmount = 1_00000000L;
				long createdTimestamp = System.currentTimeMillis();

				DeployAtTransaction deployA = deployAtWithTimestamp(repository, deployer, creationBytes, fundingAmount, createdTimestamp, "A");
				DeployAtTransaction deployB = deployAtWithTimestamp(repository, deployer, creationBytes, fundingAmount, createdTimestamp, "B");

				String addressA = deployA.getATAccount().getAddress();
				String addressB = deployB.getATAccount().getAddress();

				ATData atDataA = repository.getATRepository().fromATAddress(addressA);
				ATData atDataB = repository.getATRepository().fromATAddress(addressB);
				assertEquals(atDataA.getCreation(), atDataB.getCreation());

				// Mint another block so both ATs execute in the same height.
				BlockUtils.mintBlock(repository);
				int height = repository.getBlockRepository().getBlockchainHeight();

				List<String> expectedOrder = Arrays.asList(addressA, addressB);
				expectedOrder.sort(String::compareTo);

				List<String> executableOrder = repository.getATRepository().getAllExecutableATs(height).stream()
						.map(ATData::getATAddress)
						.filter(address -> address.equals(addressA) || address.equals(addressB))
						.collect(Collectors.toList());
				assertEquals(expectedOrder, executableOrder);

				List<String> stateOrder = repository.getATRepository().getBlockATStatesAtHeight(height).stream()
						.map(ATStateData::getATAddress)
						.filter(address -> address.equals(addressA) || address.equals(addressB))
						.collect(Collectors.toList());
				assertEquals(expectedOrder, stateOrder);
			} finally {
				if (originalTrigger == null)
					featureTriggers.remove(triggerKey);
				else
					featureTriggers.put(triggerKey, originalTrigger);
			}
		}
	}

	private DeployAtTransaction deployAtWithTimestamp(Repository repository, PrivateKeyAccount deployer, byte[] creationBytes,
			long fundingAmount, long timestamp, String suffix) throws DataException {
		byte[] lastReference = deployer.getLastReference();
		assertNotNull(String.format("Qortal account %s has no last reference", deployer.getAddress()), lastReference);

		Long fee = null;
		String name = "Test AT " + suffix;
		String description = "Test AT " + suffix;
		String atType = "Test";
		String tags = "TEST";

		BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, Group.NO_GROUP, lastReference, deployer.getPublicKey(), fee, null);
		DeployAtTransactionData deployAtTransactionData = new DeployAtTransactionData(baseTransactionData, name, description, atType, tags, creationBytes, fundingAmount, Asset.QORT);

		DeployAtTransaction deployAtTransaction = new DeployAtTransaction(repository, deployAtTransactionData);

		fee = deployAtTransaction.calcRecommendedFee();
		deployAtTransactionData.setFee(fee);

		TransactionUtils.signAndMint(repository, deployAtTransactionData, deployer);

		return deployAtTransaction;
	}
}
