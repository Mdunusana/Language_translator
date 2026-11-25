// Small, dependency-free charts for the dashboard, following the project's chart rules:
//  - one series → one hue (the accent), no legend box; the card title names the series
//  - columns ≤ 24px wide, 4px rounded data-end, square at the baseline, one baseline
//  - hairline, recessive gridlines; clean round ticks
//  - every mark has a hover AND keyboard-focus tooltip; values are also in a table view
//  - all labels are set with textContent (never innerHTML)

const SVG_NS = 'http://www.w3.org/2000/svg';
const MAX_BAR = 24;

function svgEl(name, attributes = {}) {
  const el = document.createElementNS(SVG_NS, name);
  for (const [key, value] of Object.entries(attributes)) el.setAttribute(key, String(value));
  return el;
}

/** Clean axis maximum and step: 0 / 1 / 2 / 5 / 10 / 20 / 50 … so ticks read naturally. */
export function niceScale(max, targetTicks = 4) {
  if (max <= 0) return { max: 4, step: 1 };
  const rough = max / targetTicks;
  const magnitude = 10 ** Math.floor(Math.log10(rough));
  const step = [1, 2, 5, 10].map((m) => m * magnitude).find((s) => s >= rough) ?? 10 * magnitude;
  return { max: Math.max(step, Math.ceil(max / step) * step), step: Math.max(1, step) };
}

/** Column path: square bottom corners on the baseline, 4px rounded top. */
function columnPath(x, y, width, height) {
  const r = Math.min(4, width / 2, height);
  return `M${x},${y + height} V${y + r} Q${x},${y} ${x + r},${y} H${x + width - r} Q${x + width},${y} ${x + width},${y + r} V${y + height} Z`;
}

function makeTooltip(host) {
  const tip = document.createElement('div');
  tip.className = 'chart-tooltip';
  tip.setAttribute('role', 'status');
  tip.hidden = true;
  const value = document.createElement('strong');
  const label = document.createElement('span');
  tip.append(value, label);
  host.append(tip);
  return {
    show(valueText, labelText, anchorX, anchorY) {
      value.textContent = valueText;
      label.textContent = labelText;
      tip.hidden = false;
      const hostWidth = host.clientWidth;
      const tipWidth = tip.offsetWidth;
      const left = Math.min(Math.max(anchorX - tipWidth / 2, 0), hostWidth - tipWidth);
      tip.style.left = `${left}px`;
      tip.style.top = `${Math.max(anchorY - tip.offsetHeight - 10, 0)}px`;
    },
    hide() { tip.hidden = true; },
  };
}

/**
 * Column chart for a single series over time.
 * @param {HTMLElement} host  an empty, position:relative container
 * @param {{label: string, shortLabel: string, value: number}[]} data
 * @param {{valueLabel: (v: number) => string, ariaLabel: string}} options
 */
export function renderColumnChart(host, data, { valueLabel, ariaLabel }) {
  host.replaceChildren();
  const tooltip = makeTooltip(host);
  const width = Math.max(host.clientWidth, 280);
  const height = 220;
  const margin = { top: 12, right: 8, bottom: 28, left: 32 };
  const plotWidth = width - margin.left - margin.right;
  const plotHeight = height - margin.top - margin.bottom;
  const { max, step } = niceScale(Math.max(...data.map((d) => d.value), 0));

  const svg = svgEl('svg', { viewBox: `0 0 ${width} ${height}`, width, height, role: 'group', 'aria-label': ariaLabel, class: 'column-chart' });

  // Gridlines + y ticks (hairline, recessive)
  for (let tick = 0; tick <= max; tick += step) {
    const y = margin.top + plotHeight - (tick / max) * plotHeight;
    svg.append(svgEl('line', { x1: margin.left, x2: width - margin.right, y1: y, y2: y, class: tick === 0 ? 'chart-baseline' : 'chart-grid' }));
    const label = svgEl('text', { x: margin.left - 8, y: y + 4, 'text-anchor': 'end', class: 'chart-tick' });
    label.textContent = String(tick);
    svg.append(label);
  }

  const band = plotWidth / data.length;
  const barWidth = Math.min(MAX_BAR, band * 0.6);
  const labelEvery = band < 26 ? Math.ceil(26 / band) : 1; // thin x labels on narrow screens, always keep the last

  data.forEach((d, i) => {
    const x = margin.left + i * band + (band - barWidth) / 2;
    const barHeight = (d.value / max) * plotHeight;
    const y = margin.top + plotHeight - barHeight;
    const group = svgEl('g', { class: 'chart-column', tabindex: 0, role: 'img', 'aria-label': `${d.label}: ${valueLabel(d.value)}` });
    // Hit target: the whole band, full height, bigger than the mark itself
    group.append(svgEl('rect', { x: margin.left + i * band, y: margin.top, width: band, height: plotHeight, class: 'chart-hit' }));
    if (d.value > 0) group.append(svgEl('path', { d: columnPath(x, y, barWidth, barHeight), class: 'chart-bar' }));

    const show = () => tooltip.show(valueLabel(d.value), d.label, x + barWidth / 2, d.value > 0 ? y : margin.top + plotHeight);
    group.addEventListener('pointerenter', show);
    group.addEventListener('focus', show);
    group.addEventListener('pointerleave', () => tooltip.hide());
    group.addEventListener('blur', () => tooltip.hide());
    svg.append(group);

    if (i % labelEvery === 0 || i === data.length - 1) {
      const label = svgEl('text', { x: margin.left + i * band + band / 2, y: height - 8, 'text-anchor': 'middle', class: 'chart-tick' });
      label.textContent = d.shortLabel;
      svg.append(label);
    }
  });

  host.append(svg);
}

/**
 * Ranked horizontal bars (a "meter list"): name, bar and figure per row, one hue, largest first.
 * @param {HTMLElement} host
 * @param {{name: string, value: number, detail: string}[]} rows  already sorted
 */
export function renderBarList(host, rows) {
  host.replaceChildren();
  const max = Math.max(...rows.map((row) => row.value), 1);
  const list = document.createElement('ol');
  list.className = 'bar-list';
  for (const row of rows) {
    const item = document.createElement('li');
    const name = Object.assign(document.createElement('span'), { className: 'bar-list-name', textContent: row.name });
    const track = Object.assign(document.createElement('span'), { className: 'bar-list-track' });
    const fill = Object.assign(document.createElement('span'), { className: 'bar-list-fill' });
    fill.style.width = `${Math.max((row.value / max) * 100, 2)}%`;
    track.append(fill);
    const figure = Object.assign(document.createElement('span'), { className: 'bar-list-figure', textContent: row.detail });
    item.append(name, track, figure);
    list.append(item);
  }
  host.append(list);
}
