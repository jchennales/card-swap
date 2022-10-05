package com.gmail.jchennales.cardswap;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
public class Card {
	private String code;
	@ToString.Exclude
	private TeamMember owner;
	private String status;
}
