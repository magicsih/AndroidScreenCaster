"use strict";

const tabs = Array.from(document.querySelectorAll("[data-receiver]"));
function selectReceiver(tab) {
  for (const item of tabs) {
    const selected = item === tab;
    item.setAttribute("aria-selected", String(selected));
    item.tabIndex = selected ? 0 : -1;
    document.getElementById(item.getAttribute("aria-controls")).hidden =
      !selected;
  }
  document.querySelector(".copy-status").textContent = "";
}
for (const tab of tabs) {
  tab.addEventListener("click", () => selectReceiver(tab));
  tab.addEventListener("keydown", (event) => {
    if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
    event.preventDefault();
    let index = tabs.indexOf(tab);
    if (event.key === "Home") index = 0;
    else if (event.key === "End") index = tabs.length - 1;
    else
      index =
        (index + (event.key === "ArrowRight" ? 1 : -1) + tabs.length) %
        tabs.length;
    selectReceiver(tabs[index]);
    tabs[index].focus();
  });
}

for (const button of document.querySelectorAll("[data-copy]")) {
  button.addEventListener("click", async () => {
    const command = document.getElementById(button.dataset.copy);
    const status = document.querySelector(".copy-status");
    try {
      await navigator.clipboard.writeText(command.textContent);
      status.textContent = "Command copied. Paste it into your terminal.";
    } catch {
      const selection = window.getSelection();
      const range = document.createRange();
      range.selectNodeContents(command);
      selection.removeAllRanges();
      selection.addRange(range);
      status.textContent = "Command selected. Use your browser’s Copy command.";
    }
  });
}

document.getElementById("play-demo").addEventListener("click", (event) => {
  event.preventDefault();
  const player = document.createElement("iframe");
  player.title = "Original AndroidScreenCaster demonstration on YouTube";
  player.src =
    "https://www.youtube-nocookie.com/embed/2AN6EfArfZE?autoplay=1&rel=0";
  player.allow = "autoplay; encrypted-media; picture-in-picture; fullscreen";
  player.allowFullscreen = true;
  player.referrerPolicy = "strict-origin-when-cross-origin";
  document.querySelector(".video-slot").replaceChildren(player);
  player.focus();
});
