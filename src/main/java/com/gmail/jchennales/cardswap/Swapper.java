package com.gmail.jchennales.cardswap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Swapper {

	// Maximum allowed difference between awarded cards and contributed cards. Should be at least 1.
	// Increasing this number makes members with many offered cards more "collaborative" with the group
	// by allowing others to swap more wanted cards for unwanted ones.
	private static final int maxAwardContributionDistance = 1;
	
	public static List<Card> readCardsFromString(String s, TeamMember owner) {

		List<Card> cards = new ArrayList<>();

		String prefix;
		String suffixes[];
		
		int colonPos = s.indexOf(':');
		if (colonPos < 0) {
			// Simple format, a single card code in this string, just build the card from it.
			Card c = new Card(s.trim().toUpperCase(), owner, "O");
			cards.add(c);
		}
		else {
			// Complex format, a prefix followed by a colon and then multiple card suffixes for that prefix separated by commas
			prefix = s.substring(0, colonPos).toUpperCase();
			suffixes = s.substring(colonPos + 1).trim().split(",\\s*");
			for (String suffix : suffixes) {
				Card c = new Card(prefix + suffix, owner, "O");
				cards.add(c);
			}
		}
		
		return cards;

	}

	public static List<Card> readCards(String resName, TeamMember owner) throws IOException {

		List<Card> cards = new ArrayList<>();
		
		BufferedReader cr = new BufferedReader(new InputStreamReader(Swapper.class.getClassLoader().getResourceAsStream(resName)));

		String line;
		while ((line = cr.readLine()) != null) {
			cards.addAll(readCardsFromString(line, owner));
		}

		return cards;

	}

	public static List<TeamMember> readTeamMembers(String resName) throws IOException {

		List<TeamMember> members = new ArrayList<>();
		
		BufferedReader tmr = new BufferedReader(new InputStreamReader(Swapper.class.getClassLoader().getResourceAsStream(resName)));

		String memberName;
		while ((memberName = tmr.readLine()) != null) {
			TeamMember tm = new TeamMember();
			List<Card> offeredCards = new ArrayList<>();
			List<Card> wantedCards = new ArrayList<>();
			try {
				offeredCards = readCards(memberName + "-offered.txt", tm);
			}
			catch (Exception e) {
				System.err.printf("Cannot read offered file for team member %s: %s\n", memberName, e.getMessage());
			}
			try {
				wantedCards = readCards(memberName + "-wanted.txt", tm);
			}
			catch (Exception e) {
				System.err.printf("Cannot read wanted file for team member %s: %s\n", memberName, e.getMessage());
			}
			if (offeredCards.isEmpty()) {
				System.err.printf("Skipping member with no offers: %s\n", memberName);
			}
			else {
				tm.setName(memberName);
				tm.setOfferedCards(offeredCards);
				tm.setWantedCards(wantedCards);
				tm.setAwardedCards(new ArrayList<>());
				tm.setGivenCards(new ArrayList<>());
				members.add(tm);
			}
		}

		return members;

	}

	private static void populateOfferedCardPool(Map<String, List<Card>> cardPool, List<TeamMember> teamMembers) {
		
		for (TeamMember member : teamMembers) {
			List<Card> memberCards = member.getOfferedCards();
			for (Card card : memberCards) {
				List<Card> cardList = cardPool.get(card.getCode());
				if (cardList == null) cardList = new ArrayList<>();
				cardList.add(card);
				cardPool.put(card.getCode(), cardList);
			}
		}
		
	}

	private static int memberScore(TeamMember teamMember, String mode) {
		return ("N".equals(mode)?
				teamMember.getGivenCards().size() - teamMember.getAwardedCards().size() :
				teamMember.getOfferedCards().size() - teamMember.getAwardedCards().size());
	}
	
	private static void matchCards(List<TeamMember> teamMembers, Map<String, List<Card>> offeredCardPool, String mode) {

		TeamMember bestMember = null;
		int score = 0;
		
		do {
			bestMember = null;
			int bestScore = 0;
			int bestAwarded = 0;
			int bestOffered = 0;
			score = 0;
	
			// Find the most appropriate team member to award a card from the pool
			for (TeamMember teamMember : teamMembers) {
				
				// While looking for wanted cards, start with the number of contributed cards as "credit"
				// While returning own and other cards simply use the original offered list size
				score = memberScore(teamMember, mode);
				
				// Check the basic score
				if (bestMember == null || (score >= bestScore)) {
					
					if (bestMember != null && score == bestScore) {
						// tied on score. look at pure awarded count
						if (teamMember.getAwardedCards().size() <= bestAwarded) {
							if (teamMember.getAwardedCards().size() == bestAwarded) {
								// tied on awarded. look at offer count. a tie here and it's first on list wins
								if (teamMember.getOfferedCards().size() <= bestOffered) continue;
							}
						}
						else {
							continue;
						}
					}
					
					// There may not be any wanted cards left for this member
					if ("N".equals(mode) && teamMember.isNoMoreWantedInPool()) continue;
					// There may not be any own cards left for this member
					if ("K".equals(mode) && teamMember.isNoMoreOwnInPool()) continue;
					// They cannot take more cards than they brought in
					if (teamMember.getAwardedCards().size() >= teamMember.getOfferedCards().size()) continue;
					// The (award - contribution) difference may not exceed the the defined limit
					if ("N".equals(mode) && teamMember.getAwardedCards().size() - teamMember.getGivenCards().size() >= maxAwardContributionDistance) continue;

					bestMember = teamMember;
					bestScore = score;
					bestAwarded = teamMember.getAwardedCards().size();
					bestOffered = teamMember.getOfferedCards().size();
				}
				
			}
			
			// Show status per cycle
			System.out.printf("\nSelected team member: %s\n", (bestMember == null)? "none" : bestMember.getName());
			for (TeamMember teamMember : teamMembers) {
				String xmode = mode;
				int xscore = memberScore(teamMember, mode);
				if ("N".equals(mode) && teamMember.isNoMoreWantedInPool()) xmode = "_";
				if ("K".equals(mode) && teamMember.isNoMoreOwnInPool()) xmode = " ";
				System.out.printf("stat (%s): %s %d\t%d\t%d\t%d\t%d\n", xmode, teamMember.getName(), teamMember.getGivenCards().size(), teamMember.getAwardedCards().size(), teamMember.getGivenCards().size() - teamMember.getAwardedCards().size(), teamMember.getOfferedCards().size() - teamMember.getAwardedCards().size(), xscore);
			}
			
			// If we cannot find an eligible team member to whom award a card in this cycle just leave 
			if (bestMember == null) break;
			
			Card matchedCard = null;
			List<Card> offered = null;

			if ("N".equals(mode)) {
				// Find a wanted card for this team member
				List<Card> wantedCards = bestMember.getWantedCards();
				for (Card card : wantedCards) {
					if (offeredCardPool.containsKey(card.getCode())) {
						offered = offeredCardPool.get(card.getCode()); 
						matchedCard = offered.get(0);
						matchedCard.setStatus("N");
						break;
					}
				}
			}
			else if ("K".equals(mode)) {
				// Try to give back one of its own offered cards
				for (String cardCode : offeredCardPool.keySet()) {
					offered = offeredCardPool.get(cardCode); 
					for (Card c : offered) {
						if (c.getOwner().equals(bestMember)) {
							matchedCard = c;
							matchedCard.setStatus("K");
							break;
						}
					}
					if (matchedCard != null) break;
				}
			}
			else if ("U".equals(mode)) {
				// Simply award any card from the pool
				String cardCode = offeredCardPool.keySet().iterator().next();
				offered = offeredCardPool.get(cardCode); 
				matchedCard = offered.get(0);
				matchedCard.setStatus("U");
			}
			
			if (matchedCard != null) {

				// Award the card to this member and credit its owner
				bestMember.getAwardedCards().add(matchedCard);
				matchedCard.getOwner().getGivenCards().add(matchedCard);

				// Remove any card with this code from the member's wanted list
				final Card removeCard = matchedCard;
				bestMember.getWantedCards().removeIf(x -> removeCard.getCode().equals(x.getCode()));

				// Remove the card from the offer pool and remove that code bucket if empty 
				offered.remove(removeCard);
				if (offered.isEmpty()) offeredCardPool.remove(removeCard.getCode());
				
				System.out.printf("Awarded card %s from %s to %s\n", matchedCard.getCode(), matchedCard.getOwner().getName(), bestMember.getName());
			}
			else {
				if ("N".equals(mode)) {
					bestMember.setNoMoreWantedInPool(true);
					System.out.printf("No more wanted cards in offer pool for %s\n", bestMember.getName());
				}
				else if ("K".equals(mode)) {
					bestMember.setNoMoreOwnInPool(true);
					System.out.printf("No more own cards in offer pool for %s\n", bestMember.getName());
				}					
			}
			
		} while (!offeredCardPool.isEmpty());
		
	}

	public static void main(String[] args) throws IOException {

		if (args.length > 0 && args[0].equals("C")) {
			convertListFormat();
			return;
		}
		
		Map<String, List<Card>> offeredCardPool = new HashMap<>();
		
		List <TeamMember> teamMembers = readTeamMembers("team.txt");
		
		populateOfferedCardPool(offeredCardPool, teamMembers);

		// Distribute wanted cards as fairly as we can
		matchCards(teamMembers, offeredCardPool, "N");
		// Return same cards if available to owners
		matchCards(teamMembers, offeredCardPool, "K");
		// Fill out remaining slots with anything left in the offer pool
		matchCards(teamMembers, offeredCardPool, "U");
		
		System.out.println("\n\nAwards summary (Origin/CardCode/Destination/SwapType):");
		for (TeamMember teamMember : teamMembers) {
			for (Card awardedCard : teamMember.getAwardedCards()) {
				if (!"K".equals(awardedCard.getStatus()))
				System.out.printf("%s\t%s\t%s\t%s\n", awardedCard.getOwner().getName(), awardedCard.getCode(), teamMember.getName(), awardedCard.getStatus());
			}
		}

		System.out.println("\n\nExchange summary (Team-member/GiveCardCode/ReceiveCardCode/SwapType):");
		for (TeamMember teamMember : teamMembers) {
			teamMember.getAwardedCards().removeIf(card -> card.getStatus().equals("K"));
			teamMember.getGivenCards().removeIf(card -> card.getStatus().equals("K"));
			if (teamMember.getAwardedCards().size() != teamMember.getGivenCards().size()) {
				System.err.printf("Incorrect number of cards swapped!!! member: %s awarded: %d given: %d\n", teamMember.getName(), teamMember.getAwardedCards().size(), teamMember.getGivenCards().size());
			}
			else {
				for (int i = 0; i < teamMember.getAwardedCards().size(); i++) {
					System.out.printf("%s\t%s\t%s\t%s\n", teamMember.getName(), teamMember.getGivenCards().get(i).getCode(), teamMember.getAwardedCards().get(i).getCode(), teamMember.getAwardedCards().get(i).getStatus());
				}
			}
		}

	}

	private static void convertListFormat() throws IOException {

		BufferedReader cr = new BufferedReader(new InputStreamReader(System.in));

		String line;
		while ((line = cr.readLine()) != null) {
			for (Card card : readCardsFromString(line, null)) {
				System.out.println(card.getCode());
			}
		}
		
	}

}
