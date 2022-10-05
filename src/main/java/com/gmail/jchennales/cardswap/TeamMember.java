package com.gmail.jchennales.cardswap;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TeamMember {
	private String name;
	private List<Card> offeredCards;
	private List<Card> wantedCards;
	private List<Card> awardedCards;
	private List<Card> givenCards;
	private boolean noMoreWantedInPool = false;
	private boolean noMoreOwnInPool = false;
}
