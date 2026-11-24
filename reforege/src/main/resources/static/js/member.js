document.addEventListener("DOMContentLoaded", () => {
	const loginIdInput = document.querySelector("#loginId");
	const emailInput = document.querySelector("#email");
	const loginIdResult = document.querySelector("[data-login-id-result]");
	const emailResult = document.querySelector("[data-email-result]");

	async function checkDuplicate(path, parameter, value, output) {
		if (!value.trim()) {
			output.textContent = "값을 먼저 입력해 주세요.";
			output.className = "field-result error";
			return;
		}

		try {
			const response = await window.CompickUI.fetch(
				`${path}?${parameter}=${encodeURIComponent(value.trim())}`
			);
			if (!response.ok) {
				throw new Error("중복 확인 요청에 실패했습니다.");
			}
			const result = await response.json();
			output.textContent = result.available
				? "사용할 수 있습니다."
				: "이미 사용 중입니다.";
			output.className = `field-result ${result.available ? "success" : "error"}`;
		} catch (error) {
			output.textContent = error.message;
			output.className = "field-result error";
		}
	}

	document.querySelector("[data-check-login-id]")?.addEventListener("click", () => {
		checkDuplicate(
			"/api/members/check-login-id",
			"loginId",
			loginIdInput.value,
			loginIdResult
		);
	});

	document.querySelector("[data-check-email]")?.addEventListener("click", () => {
		checkDuplicate(
			"/api/members/check-email",
			"email",
			emailInput.value,
			emailResult
		);
	});

	document.querySelector("[data-phone-input]")?.addEventListener("input", (event) => {
		const digits = event.target.value.replace(/\D/g, "").slice(0, 11);
		if (digits.length < 4) {
			event.target.value = digits;
		} else if (digits.length < 8) {
			event.target.value = `${digits.slice(0, 3)}-${digits.slice(3)}`;
		} else if (digits.length < 11) {
			event.target.value = `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`;
		} else {
			event.target.value = `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
		}
	});
});
