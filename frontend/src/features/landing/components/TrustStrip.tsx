export const TrustStrip = () => (
  <section className="border-b border-slate-200 bg-white py-10">
    <div className="mx-auto max-w-7xl px-4 sm:px-6">
      <p className="text-center text-xs font-semibold tracking-wider text-slate-400 uppercase">Được tin dùng bởi các doanh nghiệp & cư dân tại TP.HCM</p>
      <div className="mt-6 grid grid-cols-2 items-center gap-6 text-center sm:grid-cols-3 lg:grid-cols-6">
        {['An Phát Office', 'Nhà Xanh Realty', 'Sora Coworking', 'Lumi Residence', 'Kite Studio', 'Hòa Bình Tower'].map((n) => (
          <span key={n} className="text-base font-bold tracking-tight text-slate-300">{n}</span>
        ))}
      </div>
    </div>
  </section>
);
