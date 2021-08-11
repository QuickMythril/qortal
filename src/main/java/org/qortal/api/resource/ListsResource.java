package org.qortal.api.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.qortal.api.*;
import org.qortal.api.model.AddressListRequest;
import org.qortal.crypto.Crypto;
import org.qortal.data.account.AccountData;
import org.qortal.list.ResourceListManager;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;


@Path("/lists")
@Tag(name = "Lists")
public class ListsResource {

	@Context
	HttpServletRequest request;
	
	@POST
	@Path("/blacklist/address/{address}")
	@Operation(
		summary = "Add a QORT address to the local blacklist",
		responses = {
			@ApiResponse(
				description = "Returns true on success, or an exception on failure",
					content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "boolean"))
			)
		}
	)
	@ApiErrors({ApiError.INVALID_ADDRESS, ApiError.ADDRESS_UNKNOWN, ApiError.REPOSITORY_ISSUE})
	public String addAddressToBlacklist(@PathParam("address") String address) {
		if (!Crypto.isValidAddress(address))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ADDRESS);

		try (final Repository repository = RepositoryManager.getRepository()) {
			AccountData accountData = repository.getAccountRepository().getAccount(address);
			// Not found?
			if (accountData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.ADDRESS_UNKNOWN);

			// Valid address, so go ahead and blacklist it
			boolean success = ResourceListManager.getInstance().addAddressToBlacklist(address, true);

			return success ? "true" : "false";
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@POST
	@Path("/blacklist/addresses")
	@Operation(
			summary = "Add one or more QORT addresses to the local blacklist",
			requestBody = @RequestBody(
					required = true,
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON,
							schema = @Schema(
									implementation = AddressListRequest.class
							)
					)
			),
			responses = {
					@ApiResponse(
							description = "Returns true if all addresses were processed, false if any couldn't be " +
									"processed, or an exception on failure. If false or an exception is returned, " +
									"the list will not be updated, and the request will need to be re-issued.",
							content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "boolean"))
					)
			}
	)
	@ApiErrors({ApiError.INVALID_ADDRESS, ApiError.ADDRESS_UNKNOWN, ApiError.REPOSITORY_ISSUE})
	public String addAddressesToBlacklist(AddressListRequest addressListRequest) {
		if (addressListRequest == null || addressListRequest.addresses == null) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);
		}

		int successCount = 0;
		int errorCount = 0;

		try (final Repository repository = RepositoryManager.getRepository()) {

			for (String address : addressListRequest.addresses) {

				if (!Crypto.isValidAddress(address)) {
					errorCount++;
					continue;
				}

				AccountData accountData = repository.getAccountRepository().getAccount(address);
				// Not found?
				if (accountData == null) {
					errorCount++;
					continue;
				}

				// Valid address, so go ahead and blacklist it
				boolean success = ResourceListManager.getInstance().addAddressToBlacklist(address, false);
				if (success) {
					successCount++;
				}
				else {
					errorCount++;
				}
			}
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}

		if (successCount > 0 && errorCount == 0) {
			// All were successful, so save the blacklist
			ResourceListManager.getInstance().saveBlacklist();
			return "true";
		}
		else {
			// Something went wrong, so revert
			ResourceListManager.getInstance().revertBlacklist();
			return "false";
		}
	}


	@DELETE
	@Path("/blacklist/address/{address}")
	@Operation(
			summary = "Remove a QORT address from the local blacklist",
			responses = {
					@ApiResponse(
							description = "Returns true on success, or an exception on failure",
							content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "boolean"))
					)
			}
	)
	@ApiErrors({ApiError.INVALID_ADDRESS, ApiError.ADDRESS_UNKNOWN, ApiError.REPOSITORY_ISSUE})
	public String removeAddressFromBlacklist(@PathParam("address") String address) {
		if (!Crypto.isValidAddress(address))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ADDRESS);

		try (final Repository repository = RepositoryManager.getRepository()) {
			AccountData accountData = repository.getAccountRepository().getAccount(address);
			// Not found?
			if (accountData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.ADDRESS_UNKNOWN);

			// Valid address, so go ahead and blacklist it
			boolean success = ResourceListManager.getInstance().removeAddressFromBlacklist(address, true);

			return success ? "true" : "false";
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

	@DELETE
	@Path("/blacklist/addresses")
	@Operation(
			summary = "Remove one or more QORT addresses from the local blacklist",
			requestBody = @RequestBody(
					required = true,
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON,
							schema = @Schema(
									implementation = AddressListRequest.class
							)
					)
			),
			responses = {
					@ApiResponse(
							description = "Returns true if all addresses were processed, false if any couldn't be " +
									"processed, or an exception on failure. If false or an exception is returned, " +
									"the list will not be updated, and the request will need to be re-issued.",
							content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "boolean"))
					)
			}
	)
	@ApiErrors({ApiError.INVALID_ADDRESS, ApiError.ADDRESS_UNKNOWN, ApiError.REPOSITORY_ISSUE})
	public String removeAddressesFromBlacklist(AddressListRequest addressListRequest) {
		if (addressListRequest == null || addressListRequest.addresses == null) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);
		}

		int successCount = 0;
		int errorCount = 0;

		try (final Repository repository = RepositoryManager.getRepository()) {

			for (String address : addressListRequest.addresses) {

				if (!Crypto.isValidAddress(address)) {
					errorCount++;
					continue;
				}

				AccountData accountData = repository.getAccountRepository().getAccount(address);
				// Not found?
				if (accountData == null) {
					errorCount++;
					continue;
				}

				// Valid address, so go ahead and blacklist it
				// Don't save as we will do this at the end of the process
				boolean success = ResourceListManager.getInstance().removeAddressFromBlacklist(address, false);
				if (success) {
					successCount++;
				}
				else {
					errorCount++;
				}
			}
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}

		if (successCount > 0 && errorCount == 0) {
			// All were successful, so save the blacklist
			ResourceListManager.getInstance().saveBlacklist();
			return "true";
		}
		else {
			// Something went wrong, so revert
			ResourceListManager.getInstance().revertBlacklist();
			return "false";
		}
	}

	@GET
	@Path("/blacklist/addresses")
	@Operation(
			summary = "Fetch the list of blacklisted addresses",
			responses = {
					@ApiResponse(
							description = "A JSON array of addresses",
							content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = String.class)))
					)
			}
	)
	public String getAddressBlacklist() {
		return ResourceListManager.getInstance().getBlacklistJSONString();
	}

	@GET
	@Path("/blacklist/address/{address}")
	@Operation(
			summary = "Check if an address is present in the local blacklist",
			responses = {
					@ApiResponse(
							description = "Returns true or false if the list was queried, or an exception on failure",
							content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "boolean"))
					)
			}
	)
	@ApiErrors({ApiError.INVALID_ADDRESS, ApiError.ADDRESS_UNKNOWN, ApiError.REPOSITORY_ISSUE})
	public String checkAddressInBlacklist(@PathParam("address") String address) {
		if (!Crypto.isValidAddress(address))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ADDRESS);

		try (final Repository repository = RepositoryManager.getRepository()) {
			AccountData accountData = repository.getAccountRepository().getAccount(address);
			// Not found?
			if (accountData == null)
				throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.ADDRESS_UNKNOWN);

			// Valid address, so go ahead and blacklist it
			boolean blacklisted = ResourceListManager.getInstance().isAddressInBlacklist(address);

			return blacklisted ? "true" : "false";
		} catch (DataException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
		}
	}

}
