import { createContext, useContext, useEffect, useState } from 'react';

export const BRAND_DEFAULTS = {
  brandName:   'API Gateway',
  brandLogo:   '',
  loginBgFrom: '#0f172a',
  loginBgTo:   '#1e3a5f',
  accentColor: '#1d4ed8',
};

function darkenHex(hex, factor = 0.82) {
  const n = parseInt(hex.replace('#', ''), 16);
  const r = Math.round(((n >> 16) & 0xff) * factor);
  const g = Math.round(((n >>  8) & 0xff) * factor);
  const b = Math.round(( n        & 0xff) * factor);
  return `#${[r, g, b].map(x => x.toString(16).padStart(2, '0')).join('')}`;
}

const BrandContext = createContext(null);

export function BrandProvider({ children }) {
  const [brand, setBrand] = useState(() => {
    try {
      return { ...BRAND_DEFAULTS, ...JSON.parse(localStorage.getItem('gw_brand') || '{}') };
    } catch {
      return { ...BRAND_DEFAULTS };
    }
  });

  useEffect(() => {
    localStorage.setItem('gw_brand', JSON.stringify(brand));
    document.documentElement.style.setProperty('--accent',         brand.accentColor);
    document.documentElement.style.setProperty('--accent-hover',   darkenHex(brand.accentColor));
    document.documentElement.style.setProperty('--sidebar-active', brand.accentColor);
  }, [brand]);

  const update = (patch) => setBrand(b => ({ ...b, ...patch }));
  const reset  = ()       => setBrand({ ...BRAND_DEFAULTS });

  return (
    <BrandContext.Provider value={{ brand, update, reset }}>
      {children}
    </BrandContext.Provider>
  );
}

export const useBrand = () => useContext(BrandContext);