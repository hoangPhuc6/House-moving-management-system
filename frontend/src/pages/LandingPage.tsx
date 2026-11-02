import { useNavigate } from 'react-router-dom';
import { BentoFeatures } from '@/features/landing/components/BentoFeatures';
import { CtaBand } from '@/features/landing/components/CtaBand';
import { Faq } from '@/features/landing/components/Faq';
import { Hero } from '@/features/landing/components/Hero';
import { Pricing } from '@/features/landing/components/Pricing';
import { ProcessTimeline } from '@/features/landing/components/ProcessTimeline';
import { SiteFooter } from '@/features/landing/components/SiteFooter';
import { SiteHeader } from '@/features/landing/components/SiteHeader';
import { Testimonials } from '@/features/landing/components/Testimonials';
import { TrustStrip } from '@/features/landing/components/TrustStrip';
import { ROUTES } from '@/routes/paths';

export const LandingPage = () => {
  const navigate = useNavigate();
  const login = () => navigate(ROUTES.login);
  const book = () => navigate(ROUTES.customer.create);

  return (
    <div>
      <SiteHeader onLogin={login} onBook={book} />
      <Hero onBook={book} />
      <TrustStrip />
      <BentoFeatures />
      <ProcessTimeline />
      <Pricing onBook={book} />
      <Testimonials />
      <Faq />
      <CtaBand onBook={book} />
      <SiteFooter />
    </div>
  );
};

// ---------------------------------------------------------------------------
