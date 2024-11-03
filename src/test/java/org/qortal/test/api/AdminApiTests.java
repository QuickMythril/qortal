package org.qortal.test.api;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.qortal.api.restricted.resource.AdminResource;
import org.qortal.repository.DataException;
import org.qortal.settings.Settings;
import org.qortal.test.common.ApiCommon;
import org.qortal.test.common.Common;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AdminApiTests extends ApiCommon {

	private AdminResource adminResource;

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@Before
	public void buildResource() {
		this.adminResource = (AdminResource) ApiCommon.buildResource(AdminResource.class);
	}

	@Test
	public void testInfo() {
		assertNotNull(this.adminResource.info());
	}

	@Test
	public void testSummary() throws IllegalAccessException {
		// Set localAuthBypassEnabled to true, since we don't need to test authentication here
		FieldUtils.writeField(Settings.getInstance(), "localAuthBypassEnabled", true, true);

		assertNotNull(this.adminResource.summary());
	}

	@Test
	public void testGetMintingAccounts() {
		assertNotNull(this.adminResource.getMintingAccounts());
	}

	@Test
	public void testSetSetting() throws IllegalAccessException {
		// Ensure localAuthBypassEnabled is true to bypass authentication
		FieldUtils.writeField(Settings.getInstance(), "localAuthBypassEnabled", true, true);
		// Set up test setting name and value
		String settingName = "maxUnconfirmedPerAccount";
		int originalValue = Settings.getInstance().getMaxUnconfirmedPerAccount();
		String newValue = "50";
		try {
			// Call the API to set the setting
			String response = this.adminResource.setSetting(null, settingName, newValue);
			// Verify the response is "true"
			assertEquals("true", response);
			// Verify that the setting has been updated
			int updatedValue = Settings.getInstance().getMaxUnconfirmedPerAccount();
			assertEquals(Integer.parseInt(newValue), updatedValue);
		} finally {
			// Reset the setting back to original value to avoid side effects
			FieldUtils.writeField(Settings.getInstance(), settingName, originalValue, true);
		}
	}

	@Test
	public void testSetInvalidSetting() throws IllegalAccessException {
		// Ensure localAuthBypassEnabled is true to bypass authentication
		FieldUtils.writeField(Settings.getInstance(), "localAuthBypassEnabled", true, true);
		// Set up invalid setting name and value
		String settingName = "nonExistentSetting";
		String newValue = "someValue";
		// Call the API to set the setting
		String response = this.adminResource.setSetting(null, settingName, newValue);
		// Verify the response is "false"
		assertEquals("false", response);
	}

	@Test
	public void testSetSettingInvalidValue() throws IllegalAccessException {
		// Ensure localAuthBypassEnabled is true to bypass authentication
		FieldUtils.writeField(Settings.getInstance(), "localAuthBypassEnabled", true, true);
		// Set up test setting name and invalid value
		String settingName = "maxUnconfirmedPerAccount";
		int originalValue = Settings.getInstance().getMaxUnconfirmedPerAccount();
		String invalidValue = "notAnInteger";
		try {
			// Call the API to set the setting
			String response = this.adminResource.setSetting(null, settingName, invalidValue);
			// Verify the response is "false"
			assertEquals("false", response);
			// Verify that the setting has not been changed
			int updatedValue = Settings.getInstance().getMaxUnconfirmedPerAccount();
			assertEquals(originalValue, updatedValue);
		} finally {
			// Reset the setting back to original value to avoid side effects
			FieldUtils.writeField(Settings.getInstance(), settingName, originalValue, true);
		}
	}
}
