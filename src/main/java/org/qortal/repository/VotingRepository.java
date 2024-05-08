package org.qortal.repository;

import org.qortal.data.voting.PollData;
import org.qortal.data.voting.VoteOnPollData;

import java.util.List;

public interface VotingRepository {

	// Polls

	List<PollData> getAllPolls(Integer limit, Integer offset, Boolean reverse) throws DataException;

	PollData fromPollName(String pollName) throws DataException;

	boolean pollExists(String pollName) throws DataException;

	void save(PollData pollData) throws DataException;

	void delete(String pollName) throws DataException;

	// Votes

	List<VoteOnPollData> getVotes(String pollName) throws DataException;

	VoteOnPollData getVote(String pollName, byte[] voterPublicKey) throws DataException;

	void save(VoteOnPollData voteOnPollData) throws DataException;

	void delete(String pollName, byte[] voterPublicKey) throws DataException;

}
