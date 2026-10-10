// Kaleidoscope Wiki: search, list filters and Minecraft tooltips. Data is embedded (search-index.js), no requests.
(function () {
  const root = window.WIKI_ROOT || "";
  const index = window.WIKI_INDEX || [];

  // ---- Search ----
  const input = document.getElementById("search");
  const results = document.getElementById("search-results");
  let selected = -1;

  function normalize(text) {
    return text.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");
  }

  function render(query) {
    const q = normalize(query.trim());
    results.innerHTML = "";
    selected = -1;
    if (!q) {
      results.classList.remove("open");
      return;
    }
    const hits = index.filter(entry => normalize(entry.n).includes(q))
      .sort((a, b) => normalize(a.n).indexOf(q) - normalize(b.n).indexOf(q))
      .slice(0, 30);
    for (const hit of hits) {
      const link = document.createElement("a");
      link.href = root + hit.u;
      const img = document.createElement("img");
      img.src = root + hit.i;
      img.width = 24;
      img.height = 24;
      img.alt = "";
      img.onerror = () => img.remove();
      const name = document.createElement("span");
      name.textContent = hit.n;
      const kind = document.createElement("small");
      kind.textContent = hit.t;
      link.append(img, name, kind);
      results.append(link);
    }
    if (!hits.length) {
      const none = document.createElement("div");
      none.className = "muted";
      none.style.padding = "0.5rem";
      none.textContent = "Nichts gefunden.";
      results.append(none);
    }
    results.classList.add("open");
  }

  if (input) {
    input.addEventListener("input", () => render(input.value));
    input.addEventListener("keydown", event => {
      const links = results.querySelectorAll("a");
      if (event.key === "ArrowDown" || event.key === "ArrowUp") {
        event.preventDefault();
        selected = Math.max(0, Math.min(links.length - 1, selected + (event.key === "ArrowDown" ? 1 : -1)));
        links.forEach((l, i) => l.classList.toggle("sel", i === selected));
      } else if (event.key === "Enter" && links.length) {
        window.location.href = links[Math.max(0, selected)].href;
      } else if (event.key === "Escape") {
        input.value = "";
        render("");
        input.blur();
      }
    });
    document.addEventListener("click", event => {
      if (!event.target.closest(".search")) {
        results.classList.remove("open");
      }
    });
    document.addEventListener("keydown", event => {
      if (event.key === "/" && document.activeElement.tagName !== "INPUT") {
        event.preventDefault();
        input.focus();
      }
    });
  }

  // ---- Filters on list pages ----
  for (const filter of document.querySelectorAll("input[data-filter]")) {
    filter.addEventListener("input", () => {
      const q = normalize(filter.value.trim());
      for (const element of document.querySelectorAll(filter.dataset.filter)) {
        const text = normalize(element.dataset.name || element.textContent + " " +
          Array.from(element.querySelectorAll("[data-tip]")).map(t => t.dataset.tip).join(" "));
        element.classList.toggle("hidden", q !== "" && !text.includes(q));
      }
      for (const section of document.querySelectorAll("section")) {
        const grid = section.querySelector(".item-grid");
        if (grid) {
          section.classList.toggle("hidden", !grid.querySelector("a:not(.hidden)"));
        }
      }
    });
  }

  // ---- Minecraft tooltips ----
  const tooltip = document.getElementById("tooltip");
  document.addEventListener("mouseover", event => {
    const target = event.target.closest("[data-tip]");
    if (!target || !tooltip) {
      return;
    }
    tooltip.textContent = target.dataset.tip;
    tooltip.style.display = "block";
  });
  document.addEventListener("mousemove", event => {
    if (tooltip && tooltip.style.display === "block") {
      const x = Math.min(event.clientX + 14, window.innerWidth - tooltip.offsetWidth - 8);
      tooltip.style.left = x + "px";
      tooltip.style.top = (event.clientY - 10 - tooltip.offsetHeight) + "px";
    }
  });
  document.addEventListener("mouseout", event => {
    if (event.target.closest("[data-tip]") && tooltip) {
      tooltip.style.display = "none";
    }
  });
})();
