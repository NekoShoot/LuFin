package com.lufin.server.credit.domain;

import java.util.Arrays;

import lombok.Getter;

public enum CreditGrade {
	A_PLUS(95, 100, "A+"),
	A(90, 94, "A"),
	A_MINUS(85, 89, "A-"),
	B_PLUS(80, 84, "B+"),
	B(75, 79, "B"),
	B_MINUS(70, 74, "B-"),
	C_PLUS(65, 69, "C+"),
	C(60, 64, "C"),
	C_MINUS(55, 59, "C-"),
	D_PLUS(50, 54, "D+"),
	D(45, 49, "D"),
	D_MINUS(40, 44, "D-"),
	F_PLUS(35, 39, "F+"),
	F(30, 34, "F"),
	F_MINUS(0, 29, "F-");

	private final int min;
	private final int max;
	@Getter
	private final String display;

	CreditGrade(int min, int max, String display) {
		this.min = min;
		this.max = max;
		this.display = display;
	}

	public static CreditGrade fromScore(int score) {
		return Arrays.stream(values())
			.filter(g -> score >= g.min && score <= g.max)
			.findFirst()
			.orElse(F_MINUS);
	}
}
