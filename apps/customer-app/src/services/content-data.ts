export interface GuideArticle {
  id: string;
  title: string;
  subtitle: string;
  category: string;
  readTimeMins: number;
  author: string;
  veterinaryApproved: boolean;
  heroImageUrl: string;
  keyTakeaways: string[];
  sections: Array<{
    heading: string;
    body: string;
    tips?: string[];
  }>;
  relatedGuideIds: string[];
}

export const ARTICLES_DATA: Record<string, GuideArticle> = {
  'puppy-nutrition-0-2-mo': {
    id: 'puppy-nutrition-0-2-mo',
    title: 'Puppy Nutrition Guide (0 - 2 Months)',
    subtitle: 'Essential early feeding, mother colostrum, and starter weaning protocols.',
    category: 'Nutrition & Diet',
    readTimeMins: 4,
    author: 'Dr. Ananya Rao, MVSc (Pediatric Vet)',
    veterinaryApproved: true,
    heroImageUrl: 'https://images.unsplash.com/photo-1543466835-00a7907e9de1?auto=format&fit=crop&w=800&q=80',
    keyTakeaways: [
      'Colostrum in the first 24 hours provides vital maternal antibodies for immunity.',
      'Transition from milk to warm starter formula mash beginning at week 4.',
      'Feed 4-6 small meals daily to prevent hypoglycemia in small breeds.',
    ],
    sections: [
      {
        heading: 'Phase 1: Mother Milk & Colostrum (Weeks 0-4)',
        body: 'During the first month, newborn puppies rely entirely on maternal milk. Mother colostrum produced in the first 36 hours contains crucial immunoglobulins protecting puppies against distemper and parvovirus. Ensure all pups nurse evenly.',
        tips: ['Monitor daily weight gain: healthy puppies gain 5-10% body weight daily.', 'Keep nesting area at 28-30°C temperature.'],
      },
      {
        heading: 'Phase 2: Weaning to Starter Formula (Weeks 4-8)',
        body: 'At week 4, introduce puppy starter kibble soaked in warm puppy formula (3:1 ratio) to create a soft gruel. Gradually increase kibble ratio over 3 weeks until fully eating dry kibble at week 8.',
        tips: ['Never give cow milk as lactose causes severe diarrhea in puppies.', 'Provide fresh shallow water bowl 24/7.'],
      },
    ],
    relatedGuideIds: ['puppy-growth-2-12-mo', 'coat-skin-health'],
  },
  'puppy-growth-2-12-mo': {
    id: 'puppy-growth-2-12-mo',
    title: 'Puppy Growth Tracker (2 - 12 Months)',
    subtitle: 'Milestones, teething, vaccination schedule, and joint protection for growing dogs.',
    category: 'Growth & Care',
    readTimeMins: 5,
    author: 'Dr. K. Srinivas, DVM (Surgeon)',
    veterinaryApproved: true,
    heroImageUrl: 'https://images.unsplash.com/photo-1587300003388-59208cc962cb?auto=format&fit=crop&w=800&q=80',
    keyTakeaways: [
      'Complete core DHPPi 7-in-1 and Rabies vaccinations between weeks 8 to 16.',
      'Teething begins at month 4; provide rubber chew toys to protect furniture and gums.',
      'Avoid high-impact jumping on hard floors to prevent hip dysplasia in large breeds.',
    ],
    sections: [
      {
        heading: 'Vaccination & Deworming Milestones',
        body: 'Administer core 7-in-1 DHPPi booster shots at 8, 12, and 16 weeks, accompanied by Rabies vaccine at 16 weeks. Deworm monthly until 6 months of age.',
        tips: ['Do not visit public dog parks until 2 weeks after final 16-week booster.'],
      },
      {
        heading: 'Teething & Socialization Window',
        body: 'Baby teeth fall out between 4-6 months as adult teeth erupt. Use chilled KONG rubber toys to soothe sore gums. Expose puppies gently to new sounds, people, and car rides.',
        tips: ['Use positive reinforcement training with low-calorie treat rewards.'],
      },
    ],
    relatedGuideIds: ['puppy-nutrition-0-2-mo', 'coat-skin-health'],
  },
  'coat-skin-health': {
    id: 'coat-skin-health',
    title: 'Coat & Skin Health Masterclass',
    subtitle: 'Preventing flea allergies, hot spots, dry skin, and maintaining shiny coat luster.',
    category: 'Dermatology & Spa',
    readTimeMins: 6,
    author: 'Maya R., Certified Master Groomer',
    veterinaryApproved: true,
    heroImageUrl: 'https://images.unsplash.com/photo-1516734212186-a967f81ad0d7?auto=format&fit=crop&w=800&q=80',
    keyTakeaways: [
      'Omega-3 & 6 fatty acid supplements reduce coat shedding and soothe dry skin.',
      'Baths should be limited to 1-2 times monthly using pH-balanced pet shampoos.',
      'Daily brushing removes dead undercoat fur, preventing painful mats and hot spots.',
    ],
    sections: [
      {
        heading: 'Understanding Pet Skin pH & Bathing Rules',
        body: 'Pet skin is alkaline (pH 7.5) compared to human acidic skin (pH 5.5). Human shampoos strip protective natural skin oils, causing flaking and severe itchiness. Always use pet-formulated oatmeal or aloe shampoos.',
        tips: ['Rinse thoroughly with lukewarm water until runoff is completely clear.'],
      },
      {
        heading: 'Identifying & Combating Fleas and Mites',
        body: 'Flea Allergy Dermatitis (FAD) is the #1 cause of sudden scratching at tail base. Apply monthly topical spot-on preventatives or oral chewable tablets year-round.',
        tips: ['Wash pet bedding in 60°C hot water weekly during tick season.'],
      },
    ],
    relatedGuideIds: ['puppy-nutrition-0-2-mo', 'puppy-growth-2-12-mo'],
  },
};
