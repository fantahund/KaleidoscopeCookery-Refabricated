// Kaleidoscope Wiki: language settings, search, list filters and Minecraft tooltips.
// Data is embedded (search-index.js), no requests. Settings live in localStorage (per browser).
(function () {
  const root = window.WIKI_ROOT || "";
  const index = window.WIKI_INDEX || [];
  const html = document.documentElement;
  const KEY = "kc-wiki";

  // ---- Settings: wiki language (ui) and name language (names) ----
  function load() {
    try {
      return JSON.parse(localStorage.getItem(KEY) || "{}");
    } catch (e) {
      return {};
    }
  }

  function current() {
    return {
      ui: html.classList.contains("ui-en") ? "en" : "de",
      names: html.classList.contains("names-en") ? "en" : "de"
    };
  }

  function apply(settings) {
    html.classList.remove("ui-de", "ui-en", "names-de", "names-en");
    html.classList.add("ui-" + settings.ui, "names-" + settings.names);
    html.lang = settings.ui;
    document.title = html.dataset["title" + (settings.ui === "en" ? "En" : "De")] || document.title;
    for (const input of document.querySelectorAll("[data-ph-de]")) {
      input.placeholder = settings.ui === "en" ? input.dataset.phEn : input.dataset.phDe;
    }
    for (const toggle of document.querySelectorAll(".toggle")) {
      for (const button of toggle.querySelectorAll("button")) {
        button.classList.toggle("on", button.dataset.value === settings[toggle.dataset.setting]);
      }
    }
  }

  function save(settings) {
    try {
      localStorage.setItem(KEY, JSON.stringify(settings));
    } catch (e) {
      // private mode: the setting only lasts for this page
    }
  }

  apply(current());

  const settingsButton = document.getElementById("settings-button");
  const settingsPanel = document.getElementById("settings-panel");
  if (settingsButton) {
    settingsButton.addEventListener("click", event => {
      event.stopPropagation();
      settingsPanel.classList.toggle("open");
    });
    for (const toggle of document.querySelectorAll(".toggle")) {
      toggle.addEventListener("click", event => {
        const button = event.target.closest("button");
        if (!button) {
          return;
        }
        const settings = Object.assign(current(), load());
        settings[toggle.dataset.setting] = button.dataset.value;
        if (toggle.dataset.setting === "ui" && !load().names) {
          settings.names = button.dataset.value; // names follow the wiki language until chosen separately
        }
        save(settings);
        apply(settings);
        if (input && input.value) {
          render(input.value);
        }
      });
    }
    document.addEventListener("click", event => {
      if (!event.target.closest(".settings")) {
        settingsPanel.classList.remove("open");
      }
    });
  }

  // ---- Search ----
  const input = document.getElementById("search");
  const results = document.getElementById("search-results");
  let selected = -1;

  function normalize(text) {
    return text.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");
  }

  function render(query) {
    const q = normalize(query.trim());
    const { ui, names } = current();
    results.innerHTML = "";
    selected = -1;
    if (!q) {
      results.classList.remove("open");
      return;
    }
    const scored = [];
    for (const entry of index) {
      const shown = names === "en" ? entry.e : entry.d;
      const other = names === "en" ? entry.d : entry.e;
      const a = normalize(shown).indexOf(q);
      const b = normalize(other).indexOf(q);
      if (a >= 0 || b >= 0) {
        scored.push({ entry, shown, score: a >= 0 ? a : 100 + b });
      }
    }
    scored.sort((x, y) => x.score - y.score);
    for (const { entry, shown } of scored.slice(0, 30)) {
      const link = document.createElement("a");
      link.href = root + entry.u;
      const img = document.createElement("img");
      img.src = root + entry.i;
      img.width = 24;
      img.height = 24;
      img.alt = "";
      img.onerror = () => img.remove();
      const name = document.createElement("span");
      name.textContent = shown;
      const kind = document.createElement("small");
      kind.textContent = entry.k === "station" ? (ui === "en" ? "Station" : "Station") : "Item";
      link.append(img, name, kind);
      results.append(link);
    }
    if (!scored.length) {
      const none = document.createElement("div");
      none.className = "muted";
      none.style.padding = "0.5rem";
      none.textContent = ui === "en" ? "Nothing found." : "Nichts gefunden.";
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

  // ---- Filters on list pages (match German and English names) ----
  for (const filter of document.querySelectorAll("input[data-filter]")) {
    filter.addEventListener("input", () => {
      const q = normalize(filter.value.trim());
      for (const element of document.querySelectorAll(filter.dataset.filter)) {
        const tips = Array.from(element.querySelectorAll("[data-tip-de]")).map(t => t.dataset.tipDe + " " + t.dataset.tipEn).join(" ");
        const text = normalize((element.dataset.name || "") + " " + tips);
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

  // ---- Minecraft tooltips (name in the chosen name language) ----
  const tooltip = document.getElementById("tooltip");
  document.addEventListener("mouseover", event => {
    const target = event.target.closest("[data-tip-de]");
    if (!target || !tooltip) {
      return;
    }
    tooltip.textContent = current().names === "en" ? target.dataset.tipEn : target.dataset.tipDe;
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
    if (event.target.closest("[data-tip-de]") && tooltip) {
      tooltip.style.display = "none";
    }
  });
})();
