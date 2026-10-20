/* SG Media detaljer: personlig hilsen på forsiden og NY mærke på nye film og afsnit.
   Indlæses af JavaScript Injector pluginet i Jellyfin. */
(function () {
  'use strict';
  if (window.__sgDetaljer) return;
  window.__sgDetaljer = true;

  const NY_DAGE = window.sgNyDage || 7;
  const grænse = () => Date.now() - NY_DAGE * 864e5;

  function hilsenTekst() {
    const t = new Date().getHours();
    if (t < 5) return 'Godnat';
    if (t < 10) return 'Godmorgen';
    if (t < 12) return 'God formiddag';
    if (t < 18) return 'God eftermiddag';
    return 'Godaften';
  }

  // MinDateCreated filtreres ikke af serveren, så vi tæller selv blandt de nyeste
  async function nyeSiden(userId, type) {
    const r = await ApiClient.getItems(userId, {
      Recursive: true, IncludeItemTypes: type, SortBy: 'DateCreated', SortOrder: 'Descending',
      Fields: 'DateCreated', EnableImages: false, EnableUserData: false, Limit: 400
    });
    return (r.Items || []).filter(i => Date.parse(i.DateCreated) > grænse());
  }

  let hilsenKører = false;
  async function lavHilsen() {
    const side = document.querySelector('#indexPage:not(.hide), .homePage:not(.hide)');
    if (!side || side.querySelector('.sg-hilsen') || hilsenKører) return;
    hilsenKører = true;
    try {
      const bruger = await ApiClient.getCurrentUser();
      const el = document.createElement('div');
      el.className = 'sg-hilsen';
      const titel = document.createElement('div');
      titel.className = 'sg-hilsen-titel';
      titel.textContent = hilsenTekst() + ', ' + bruger.Name.charAt(0).toUpperCase() + bruger.Name.slice(1);
      const under = document.createElement('div');
      under.className = 'sg-hilsen-under';
      el.append(titel, under);

      const sektioner = side.querySelector('.sections, .homeSectionsContainer');
      (sektioner || side).prepend(el);

      const [film, afsnit] = await Promise.all([nyeSiden(bruger.Id, 'Movie'), nyeSiden(bruger.Id, 'Episode')]);
      const serier = new Set(afsnit.map(a => a.SeriesId)).size;
      const dele = [];
      if (film.length) dele.push(film.length + (film.length === 1 ? ' ny film' : ' nye film'));
      if (serier) dele.push('nye afsnit i ' + serier + (serier === 1 ? ' serie' : ' serier'));
      under.textContent = dele.length ? dele.join(' og ') + ' denne uge' : 'Klar til en god film?';
    } catch (e) {
      console.warn('SG hilsen', e);
    } finally {
      hilsenKører = false;
    }
  }

  const kendte = new Map();
  let kø = new Set();
  let timer = null;

  function markér(card, item) {
    if (!item) return;
    const dato = Date.parse(item.Type === 'Series' ? (item.DateLastMediaAdded || item.DateCreated) : item.DateCreated);
    const set = item.UserData && item.UserData.Played;
    const holder = card.querySelector('.cardScalable');
    if (!holder || holder.querySelector('.sg-ny')) return;
    if (dato > grænse() && !set) {
      const b = document.createElement('div');
      b.className = 'sg-ny';
      b.textContent = 'NY';
      holder.appendChild(b);
    }
  }

  async function hentKø() {
    timer = null;
    const ids = [...kø].filter(id => !kendte.has(id)).slice(0, 100);
    kø = new Set();
    if (ids.length) {
      try {
        const r = await ApiClient.getItems(ApiClient.getCurrentUserId(), {
          Ids: ids.join(','), Fields: 'DateCreated,DateLastMediaAdded', EnableImages: false
        });
        for (const id of ids) kendte.set(id, null);
        for (const it of r.Items || []) kendte.set(it.Id, it);
      } catch (e) {
        console.warn('SG NY', e);
      }
    }
    document.querySelectorAll('.card[data-id]:not([data-sg-ny])').forEach(card => {
      const id = card.getAttribute('data-id');
      if (!kendte.has(id)) return;
      card.setAttribute('data-sg-ny', '1');
      markér(card, kendte.get(id));
    });
  }

  // Knap i toppen, der åbner Jellyfin Enhanced's side med anmodninger og downloads
  const KOMMER_SNART = '#/userpluginsettings.html' + String.fromCharCode(63) + 'pageUrl' + String.fromCharCode(61) + '/JellyfinEnhanced/downloadsPage';
  function lavKommerSnart() {
    const højre = document.querySelector('.headerRight');
    if (!højre || højre.querySelector('.sg-kommer-snart')) return;
    const knap = document.createElement('a');
    knap.className = 'headerButton headerButtonRight paper-icon-button-light sg-kommer-snart';
    knap.href = KOMMER_SNART;
    knap.title = 'Kommer snart';
    knap.setAttribute('aria-label', 'Kommer snart');
    knap.innerHTML = '<span class="material-icons" aria-hidden="true">upcoming</span>';
    const søg = højre.querySelector('.headerSearchButton');
    højre.insertBefore(knap, søg || højre.firstChild);
  }

  function scan() {
    lavKommerSnart();
    const type = /Movie|Episode|Series|Season/;
    document.querySelectorAll('.card[data-id]:not([data-sg-ny])').forEach(card => {
      if (type.test(card.getAttribute('data-type') || '')) kø.add(card.getAttribute('data-id'));
      else card.setAttribute('data-sg-ny', '1');
    });
    if (kø.size && !timer) timer = setTimeout(hentKø, 400);
    lavHilsen();
  }

  function start() {
    if (typeof ApiClient === 'undefined' || !ApiClient.getCurrentUserId || !ApiClient.getCurrentUserId()) {
      return setTimeout(start, 1000);
    }
    let vent = null;
    new MutationObserver(() => {
      if (!vent) vent = setTimeout(() => { vent = null; scan(); }, 300);
    }).observe(document.body, { childList: true, subtree: true });
    scan();
  }
  start();
})();
